package com.yangzhiguo.mushroom.sync

import com.yangzhiguo.mushroom.scraper.ApiClient
import com.yangzhiguo.mushroom.scraper.DataSource
import com.yangzhiguo.mushroom.scraper.Scraper
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.sql.DriverManager

/**
 * 一次性 bootstrap 脚本:清空 data/mushroom.db,爬取
 * https://fungi.iflora.cn/#/list_species/general_directory 全部数据,
 * 通过 [ScraperToRoomMapper.toBatch] 映射成 5 张表 (1 主 + 4 子),
 * 写入 SQLite。
 *
 * 运行:
 *   ./gradlew :app:testDebugUnitTest \
 *     --tests "com.yangzhiguo.mushroom.sync.BootstrapMushroomDbTest"
 *
 * 输出:data/mushroom.db
 */
class BootstrapMushroomDbTest {

    private val outFile = File("../data/mushroom.db").apply {
        // Gradle 测试 cwd 是 app/,输出回 project root
        parentFile?.mkdirs()
    }
    private val pageSize = 50

    @Test
    fun bootstrap() {
        // ── Step 1: 清空旧 db ────────────────────────────────────────
        if (outFile.exists()) {
            println("Deleting existing ${outFile.absolutePath} (${outFile.length()} bytes)")
            outFile.delete()
        }
        File(outFile.absolutePath + "-wal").delete()
        File(outFile.absolutePath + "-shm").delete()
        require(!outFile.exists()) { "Failed to delete old mushroom.db" }

        // ── Step 2: 抓全量 ──────────────────────────────────────────
        val records = runBlocking {
            val scraper = Scraper(api = ApiClient(), pageSize = pageSize)
            scraper.fetchAll { page, total ->
                println("  page=$page  total=$total")
            }
        }
        require(records.isNotEmpty()) { "No records fetched" }
        val wantedSources = setOf(
            DataSource.GENERAL_DIRECTORY,
            DataSource.EDIBLE_FUNGI,
            DataSource.TOXIC_FUNGI,
        )
        val directoryRecords = records.filter { it.source in wantedSources }
        println("Fetched ${records.size} total; GENERAL_DIRECTORY+EDIBLE+TOXIC=${directoryRecords.size}")
        wantedSources.forEach { src ->
            val n = records.count { it.source == src }
            println("  - $src: $n records")
        }

        // ── Step 3: 写库 ────────────────────────────────────────────
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${outFile.absolutePath}").use { conn ->
            conn.createStatement().use { stmt ->
                // PRAGMA 必须在 autoCommit=false 之前执行
                stmt.execute("PRAGMA journal_mode = WAL")
                stmt.execute("PRAGMA synchronous = NORMAL")
            }
            conn.autoCommit = false
            conn.createStatement().use { stmt ->
                stmt.execute("DROP TABLE IF EXISTS mushroom_species")
                stmt.execute("DROP TABLE IF EXISTS mushroom_specimen")
                stmt.execute("DROP TABLE IF EXISTS mushroom_dna_barcode")
                stmt.execute("DROP TABLE IF EXISTS mushroom_distribution_point")
                stmt.execute("DROP TABLE IF EXISTS mushroom_image")
                stmt.execute(CREATE_SPECIES_SQL)
                stmt.execute(CREATE_SPECIMEN_SQL)
                stmt.execute(CREATE_DNA_SQL)
                stmt.execute(CREATE_DISTRIBUTION_SQL)
                stmt.execute(CREATE_IMAGE_SQL)
                CREATE_SPECIES_INDEXES.forEach { stmt.execute(it) }
                CREATE_SPECIMEN_INDEXES.forEach { stmt.execute(it) }
                CREATE_DNA_INDEXES.forEach { stmt.execute(it) }
                CREATE_DISTRIBUTION_INDEXES.forEach { stmt.execute(it) }
                CREATE_IMAGE_INDEXES.forEach { stmt.execute(it) }
            }

            val speciesStmt = conn.prepareStatement(INSERT_SPECIES_SQL)
            val specimenStmt = conn.prepareStatement(INSERT_SPECIMEN_SQL)
            val dnaStmt = conn.prepareStatement(INSERT_DNA_SQL)
            val distStmt = conn.prepareStatement(INSERT_DISTRIBUTION_SQL)
            val imageStmt = conn.prepareStatement(INSERT_IMAGE_SQL)

            val now = System.currentTimeMillis()
            var speciesCount = 0
            var specimenCount = 0
            var dnaCount = 0
            var imageCount = 0
            for (record in directoryRecords) {
                val batch = ScraperToRoomMapper.toBatch(
                    record = record,
                    regionLookup = ScraperToRoomMapper.NOOP_REGION_LOOKUP,
                    now = now,
                )
                bindSpecies(speciesStmt, batch.species[0])
                speciesStmt.addBatch()
                speciesCount++
                for (sp in batch.specimens) {
                    bindSpecimen(specimenStmt, sp); specimenStmt.addBatch(); specimenCount++
                }
                for (bc in batch.barcodes) {
                    bindDna(dnaStmt, bc); dnaStmt.addBatch(); dnaCount++
                }
                for (img in batch.images) {
                    bindImage(imageStmt, img); imageStmt.addBatch(); imageCount++
                }
                for (dp in batch.distributionPoints) {
                    bindDistribution(distStmt, dp); distStmt.addBatch()
                }
            }
            speciesStmt.executeBatch()
            specimenStmt.executeBatch()
            dnaStmt.executeBatch()
            imageStmt.executeBatch()
            distStmt.executeBatch()
            conn.commit()

            println("=== WRITE COMPLETE ===")
            println("  species: $speciesCount")
            println("  specimens: $specimenCount")
            println("  DNA barcodes: $dnaCount")
            println("  images: $imageCount")
            println("Output: ${outFile.absolutePath} (${outFile.length()} bytes)")
        }
    }

    // ── SQL DDL ──────────────────────────────────────────────────────

    private val CREATE_SPECIES_SQL = """
        CREATE TABLE mushroom_species (
            id INTEGER PRIMARY KEY,
            scraw_source TEXT NOT NULL DEFAULT 'general_directory',
            last_updated INTEGER NOT NULL,
            scientific_name TEXT NOT NULL DEFAULT '',
            chinese_name TEXT NOT NULL DEFAULT '',
            authority TEXT NOT NULL DEFAULT '',
            species_latin_genus TEXT, specific_epithet TEXT, species_common TEXT, field_identification TEXT,
            community_zh TEXT, community_la TEXT,
            phylum_zh TEXT, phylum_la TEXT,
            class_zh TEXT, class_la TEXT,
            order_zh TEXT, order_la TEXT,
            suborder_zh TEXT, suborder_la TEXT,
            family_zh TEXT NOT NULL DEFAULT '', family_la TEXT NOT NULL DEFAULT '',
            subfamily_zh TEXT, subfamily_la TEXT,
            genus_zh TEXT NOT NULL DEFAULT '', genus_la TEXT NOT NULL DEFAULT '',
            subgenus_zh TEXT, subgenus_la TEXT,
            section_zh TEXT, section_la TEXT,
            use_type TEXT NOT NULL, toxicity_level INTEGER NOT NULL, edibility TEXT NOT NULL,
            edible_fungus TEXT, medicinal_fungus TEXT, toxic_fungus TEXT,
            conditionally_fungus TEXT, mycorrhizal_fungus TEXT, saprophytic_fungus TEXT,
            parasitic_fungus TEXT, economic_use TEXT,
            habitat TEXT NOT NULL DEFAULT '', substrate TEXT, tree_species TEXT,
            climate_zone TEXT, tropical_species TEXT, subtropical_species TEXT, temperate_species TEXT,
            southwest_specific TEXT, yunnan_specific TEXT,
            is_southwest TEXT, is_xizang TEXT, is_sichuan TEXT, is_guizhou TEXT,
            is_gaoligong TEXT, is_yunnan TEXT,
            distribution_location TEXT, altitude_range TEXT,
            species_description TEXT, cap_description TEXT, cap_context TEXT,
            lamella_description TEXT, stipe_description TEXT, stipe_context TEXT,
            odor TEXT, spore_description TEXT,
            ring_description TEXT, volva_description TEXT,
            description_reference TEXT, purpose_references TEXT,
            habit_references TEXT, directory_references TEXT, directory_grade TEXT,
            its_genbank TEXT, its_genbank_url TEXT, nrlsu_genbank TEXT, nrlsu_genbank_url TEXT,
            tef1_genbank TEXT, tef1_genbank_url TEXT, rpb1_genbank TEXT, rpb1_genbank_url TEXT,
            rpb2_genbank TEXT, rpb2_genbank_url TEXT, ssu TEXT, tub2 TEXT,
            collect_user TEXT, collect_unit TEXT, research_team TEXT,
            gather_num TEXT, collection_num TEXT, collect_time TEXT,
            specimen_habitat TEXT, field_note TEXT, specimen_describe TEXT,
            resource_type TEXT, specimen_group TEXT, photo_num TEXT, create_time TEXT,
            species_checker TEXT, fill_user TEXT, is_approve TEXT,
            is_open INTEGER, borrow_status INTEGER,
            assigning_user TEXT, assigning_id TEXT, strain_number TEXT, notes TEXT, other_data TEXT,
            image_url TEXT, image_local_path TEXT, images TEXT NOT NULL DEFAULT '[]',
            source_url TEXT NOT NULL DEFAULT '', source_types TEXT NOT NULL DEFAULT '',
            is_favorite INTEGER NOT NULL DEFAULT 0, model_3d_url TEXT,
            identification_points TEXT NOT NULL DEFAULT '[]', look_alike_ids TEXT NOT NULL DEFAULT '',
            toxicity_symptoms TEXT NOT NULL DEFAULT '', season TEXT NOT NULL DEFAULT ''
        )
    """.trimIndent()

    private val CREATE_SPECIES_INDEXES = listOf(
        "CREATE INDEX idx_species_sciname ON mushroom_species(scientific_name COLLATE NOCASE)",
        "CREATE INDEX idx_species_chiname ON mushroom_species(chinese_name COLLATE NOCASE)",
        "CREATE INDEX idx_species_family ON mushroom_species(family_la, family_zh)",
        "CREATE INDEX idx_species_genus ON mushroom_species(genus_la, genus_zh)",
        "CREATE INDEX idx_species_source ON mushroom_species(scraw_source, source_types)",
    )

    private val CREATE_SPECIMEN_SQL = """
        CREATE TABLE mushroom_specimen (
            id INTEGER PRIMARY KEY,
            species_id INTEGER NOT NULL REFERENCES mushroom_species(id) ON DELETE CASCADE,
            gather_num TEXT, collection_num TEXT, strain_number TEXT,
            collect_user TEXT, collect_unit TEXT, research_team TEXT,
            collect_time TEXT, collect_country TEXT,
            collect_province TEXT, collect_province_zh TEXT,
            collect_city TEXT, collect_city_zh TEXT,
            collect_district TEXT, collect_district_zh TEXT,
            collect_village TEXT,
            latitude TEXT, longitude TEXT, altitude TEXT,
            specimen_habitat TEXT, field_note TEXT, resource_type TEXT,
            create_time TEXT, notes TEXT, source_url TEXT
        )
    """.trimIndent()

    private val CREATE_SPECIMEN_INDEXES = listOf(
        "CREATE INDEX idx_specimen_species ON mushroom_specimen(species_id)",
        "CREATE INDEX idx_specimen_gathnum ON mushroom_specimen(gather_num)",
        "CREATE INDEX idx_specimen_collno ON mushroom_specimen(collection_num)",
    )

    private val CREATE_DNA_SQL = """
        CREATE TABLE mushroom_dna_barcode (
            specimen_id INTEGER NOT NULL REFERENCES mushroom_specimen(id) ON DELETE CASCADE,
            species_id INTEGER NOT NULL,
            gene TEXT NOT NULL,
            accession TEXT, url TEXT, filename TEXT, is_public INTEGER NOT NULL DEFAULT 1,
            PRIMARY KEY (specimen_id, gene)
        )
    """.trimIndent()

    private val CREATE_DNA_INDEXES = listOf(
        "CREATE INDEX idx_dna_specimen ON mushroom_dna_barcode(specimen_id)",
        "CREATE INDEX idx_dna_gene ON mushroom_dna_barcode(gene)",
        "CREATE INDEX idx_dna_accession ON mushroom_dna_barcode(accession)",
        "CREATE INDEX idx_dna_species_gene ON mushroom_dna_barcode(species_id, gene)",
    )

    private val CREATE_DISTRIBUTION_SQL = """
        CREATE TABLE mushroom_distribution_point (
            species_id INTEGER NOT NULL REFERENCES mushroom_species(id) ON DELETE CASCADE,
            lng REAL NOT NULL, lat REAL NOT NULL,
            value TEXT, province TEXT, count INTEGER NOT NULL DEFAULT 1,
            PRIMARY KEY (species_id, lng, lat)
        )
    """.trimIndent()

    private val CREATE_DISTRIBUTION_INDEXES = listOf(
        "CREATE INDEX idx_dist_species ON mushroom_distribution_point(species_id)",
        "CREATE INDEX idx_dist_province ON mushroom_distribution_point(province)",
    )

    private val CREATE_IMAGE_SQL = """
        CREATE TABLE mushroom_image (
            species_id INTEGER NOT NULL REFERENCES mushroom_species(id) ON DELETE CASCADE,
            uf_id TEXT NOT NULL,
            uf_name TEXT, uf_src TEXT NOT NULL, uf_size INTEGER,
            ident TEXT, source TEXT NOT NULL,
            sort_order INTEGER NOT NULL DEFAULT 0, local_path TEXT,
            PRIMARY KEY (species_id, uf_id)
        )
    """.trimIndent()

    private val CREATE_IMAGE_INDEXES = listOf(
        "CREATE INDEX idx_image_species ON mushroom_image(species_id)",
        "CREATE INDEX idx_image_source ON mushroom_image(source)",
        "CREATE INDEX idx_image_sort ON mushroom_image(sort_order)",
    )

    // ── INSERT SQL ──────────────────────────────────────────────────

    private val SPECIES_INSERT_COLUMNS = """
        id, scraw_source, last_updated, scientific_name, chinese_name, authority,
        species_latin_genus, specific_epithet, species_common, field_identification,
        community_zh, community_la, phylum_zh, phylum_la, class_zh, class_la,
        order_zh, order_la, suborder_zh, suborder_la,
        family_zh, family_la, subfamily_zh, subfamily_la,
        genus_zh, genus_la, subgenus_zh, subgenus_la, section_zh, section_la,
        use_type, toxicity_level, edibility,
        edible_fungus, medicinal_fungus, toxic_fungus, conditionally_fungus,
        mycorrhizal_fungus, saprophytic_fungus, parasitic_fungus, economic_use,
        habitat, substrate, tree_species, climate_zone,
        tropical_species, subtropical_species, temperate_species,
        southwest_specific, yunnan_specific,
        is_southwest, is_xizang, is_sichuan, is_guizhou, is_gaoligong, is_yunnan,
        distribution_location, altitude_range,
        species_description, cap_description, cap_context, lamella_description,
        stipe_description, stipe_context, odor, spore_description,
        ring_description, volva_description, description_reference,
        purpose_references, habit_references, directory_references, directory_grade,
        its_genbank, its_genbank_url, nrlsu_genbank, nrlsu_genbank_url,
        tef1_genbank, tef1_genbank_url, rpb1_genbank, rpb1_genbank_url,
        rpb2_genbank, rpb2_genbank_url, ssu, tub2,
        collect_user, collect_unit, research_team, gather_num, collection_num,
        collect_time, specimen_habitat, field_note, specimen_describe,
        resource_type, specimen_group, photo_num, create_time,
        species_checker, fill_user, is_approve, is_open, borrow_status,
        assigning_user, assigning_id, strain_number, notes, other_data,
        image_url, image_local_path, images,
        source_url, source_types, is_favorite, model_3d_url,
        identification_points, look_alike_ids, toxicity_symptoms, season
    """.trimIndent()

    private val INSERT_SPECIES_SQL by lazy {
        val colCount = SPECIES_INSERT_COLUMNS.split(",").size
        "INSERT OR REPLACE INTO mushroom_species ($SPECIES_INSERT_COLUMNS) VALUES " +
            "(${(1..colCount).joinToString(",") { "?" }})"
    }

    private val INSERT_SPECIMEN_SQL = """
        INSERT OR REPLACE INTO mushroom_specimen (
            id, species_id, gather_num, collection_num, strain_number,
            collect_user, collect_unit, research_team, collect_time, collect_country,
            collect_province, collect_province_zh, collect_city, collect_city_zh,
            collect_district, collect_district_zh, collect_village,
            latitude, longitude, altitude, specimen_habitat, field_note,
            resource_type, create_time, notes, source_url
        ) VALUES (${(1..26).joinToString(",") { "?" }})
    """.trimIndent()

    private val INSERT_DNA_SQL = """
        INSERT OR REPLACE INTO mushroom_dna_barcode (
            specimen_id, species_id, gene, accession, url, filename, is_public
        ) VALUES (?,?,?,?,?,?,?)
    """.trimIndent()

    private val INSERT_DISTRIBUTION_SQL = """
        INSERT OR REPLACE INTO mushroom_distribution_point (
            species_id, lng, lat, value, province, count
        ) VALUES (?,?,?,?,?,?)
    """.trimIndent()

    private val INSERT_IMAGE_SQL = """
        INSERT OR REPLACE INTO mushroom_image (
            species_id, uf_id, uf_name, uf_src, uf_size, ident, source, sort_order, local_path
        ) VALUES (?,?,?,?,?,?,?,?,?)
    """.trimIndent()

    // ── 绑定辅助 ──────────────────────────────────────────────────

    private fun bindSpecies(ps: java.sql.PreparedStatement, e: com.yangzhiguo.mushroom.data.local.SpeciesEntity) {
        var i = 1
        ps.setInt(i++, e.id)
        ps.setString(i++, e.scrawSource)
        ps.setLong(i++, e.lastUpdated)
        ps.setString(i++, e.scientificName)
        ps.setString(i++, e.chineseName)
        ps.setString(i++, e.authority)
        ps.setString(i++, e.speciesLatinGenus)
        ps.setString(i++, e.specificEpithet)
        ps.setString(i++, e.speciesCommon)
        ps.setString(i++, e.fieldIdentification)
        ps.setString(i++, e.communityZh); ps.setString(i++, e.communityLa)
        ps.setString(i++, e.phylumZh); ps.setString(i++, e.phylumLa)
        ps.setString(i++, e.classZh); ps.setString(i++, e.classLa)
        ps.setString(i++, e.orderZh); ps.setString(i++, e.orderLa)
        ps.setString(i++, e.suborderZh); ps.setString(i++, e.suborderLa)
        ps.setString(i++, e.familyZh); ps.setString(i++, e.familyLa)
        ps.setString(i++, e.subfamilyZh); ps.setString(i++, e.subfamilyLa)
        ps.setString(i++, e.genusZh); ps.setString(i++, e.genusLa)
        ps.setString(i++, e.subgenusZh); ps.setString(i++, e.subgenusLa)
        ps.setString(i++, e.sectionZh); ps.setString(i++, e.sectionLa)
        ps.setString(i++, e.useType.name)
        ps.setInt(i++, e.toxicityLevel.level)
        ps.setString(i++, e.edibility.name)
        ps.setString(i++, e.edibleFungus); ps.setString(i++, e.medicinalFungus)
        ps.setString(i++, e.toxicFungus); ps.setString(i++, e.conditionallyFungus)
        ps.setString(i++, e.mycorrhizalFungus); ps.setString(i++, e.saprophyticFungus)
        ps.setString(i++, e.parasiticFungus); ps.setString(i++, e.economicUse)
        ps.setString(i++, e.habitat); ps.setString(i++, e.substrate)
        ps.setString(i++, e.treeSpecies); ps.setString(i++, e.climateZone)
        ps.setString(i++, e.tropicalSpecies); ps.setString(i++, e.subtropicalSpecies)
        ps.setString(i++, e.temperateSpecies); ps.setString(i++, e.southwestSpecific)
        ps.setString(i++, e.yunnanSpecific)
        ps.setString(i++, e.isSouthwest); ps.setString(i++, e.isXizang)
        ps.setString(i++, e.isSichuan); ps.setString(i++, e.isGuizhou)
        ps.setString(i++, e.isGaoligong); ps.setString(i++, e.isYunnan)
        ps.setString(i++, e.distributionLocation); ps.setString(i++, e.altitudeRange)
        ps.setString(i++, e.speciesDescription); ps.setString(i++, e.capDescription)
        ps.setString(i++, e.capContext); ps.setString(i++, e.lamellaDescription)
        ps.setString(i++, e.stipeDescription); ps.setString(i++, e.stipeContext)
        ps.setString(i++, e.odor); ps.setString(i++, e.sporeDescription)
        ps.setString(i++, e.ringDescription); ps.setString(i++, e.volvaDescription)
        ps.setString(i++, e.descriptionReference); ps.setString(i++, e.purposeReferences)
        ps.setString(i++, e.habitReferences); ps.setString(i++, e.directoryReferences)
        ps.setString(i++, e.directoryGrade)
        ps.setString(i++, e.itsGenbank); ps.setString(i++, e.itsGenbankUrl)
        ps.setString(i++, e.nrlsuGenbank); ps.setString(i++, e.nrlsuGenbankUrl)
        ps.setString(i++, e.tef1Genbank); ps.setString(i++, e.tef1GenbankUrl)
        ps.setString(i++, e.rpb1Genbank); ps.setString(i++, e.rpb1GenbankUrl)
        ps.setString(i++, e.rpb2Genbank); ps.setString(i++, e.rpb2GenbankUrl)
        ps.setString(i++, e.ssu); ps.setString(i++, e.tub2)
        ps.setString(i++, e.collectUser); ps.setString(i++, e.collectUnit)
        ps.setString(i++, e.researchTeam); ps.setString(i++, e.gatherNum)
        ps.setString(i++, e.collectionNum); ps.setString(i++, e.collectTime)
        ps.setString(i++, e.specimenHabitat); ps.setString(i++, e.fieldNote)
        ps.setString(i++, e.specimenDescribe); ps.setString(i++, e.resourceType)
        ps.setString(i++, e.specimenGroup); ps.setString(i++, e.photoNum)
        ps.setString(i++, e.createTime); ps.setString(i++, e.speciesChecker)
        ps.setString(i++, e.fillUser); ps.setString(i++, e.isApprove)
        val isOpenVal = e.isOpen; val borrowVal = e.borrowStatus
        if (isOpenVal != null) ps.setInt(i++, isOpenVal) else ps.setNull(i++, java.sql.Types.INTEGER)
        if (borrowVal != null) ps.setInt(i++, borrowVal) else ps.setNull(i++, java.sql.Types.INTEGER)
        ps.setString(i++, e.assigningUser); ps.setString(i++, e.assigningId)
        ps.setString(i++, e.strainNumber); ps.setString(i++, e.notes)
        ps.setString(i++, e.otherData)
        ps.setString(i++, e.imageUrl); ps.setString(i++, e.imageLocalPath)
        ps.setString(i++, e.images)
        ps.setString(i++, e.sourceUrl); ps.setString(i++, e.sourceTypes)
        ps.setInt(i++, if (e.isFavorite) 1 else 0)
        ps.setString(i++, e.model3dUrl)
        ps.setString(i++, e.identificationPoints); ps.setString(i++, e.lookAlikeIds)
        ps.setString(i++, e.toxicitySymptoms); ps.setString(i++, e.season)
    }

    private fun bindSpecimen(ps: java.sql.PreparedStatement, e: com.yangzhiguo.mushroom.data.local.SpecimenEntity) {
        var i = 1
        ps.setLong(i++, e.id)
        ps.setInt(i++, e.speciesId)
        ps.setString(i++, e.gatherNum); ps.setString(i++, e.collectionNum)
        ps.setString(i++, e.strainNumber)
        ps.setString(i++, e.collectUser); ps.setString(i++, e.collectUnit)
        ps.setString(i++, e.researchTeam); ps.setString(i++, e.collectTime)
        ps.setString(i++, e.collectCountry)
        ps.setString(i++, e.collectProvince); ps.setString(i++, e.collectProvinceZh)
        ps.setString(i++, e.collectCity); ps.setString(i++, e.collectCityZh)
        ps.setString(i++, e.collectDistrict); ps.setString(i++, e.collectDistrictZh)
        ps.setString(i++, e.collectVillage)
        ps.setString(i++, e.latitude); ps.setString(i++, e.longitude)
        ps.setString(i++, e.altitude)
        ps.setString(i++, e.specimenHabitat); ps.setString(i++, e.fieldNote)
        ps.setString(i++, e.resourceType); ps.setString(i++, e.createTime)
        ps.setString(i++, e.notes); ps.setString(i++, e.sourceUrl)
    }

    private fun bindDna(ps: java.sql.PreparedStatement, e: com.yangzhiguo.mushroom.data.local.DnaBarcodeEntity) {
        ps.setLong(1, e.specimenId)
        ps.setInt(2, e.speciesId)
        ps.setString(3, e.gene)
        ps.setString(4, e.accession)
        ps.setString(5, e.url)
        ps.setString(6, e.filename)
        ps.setInt(7, if (e.isPublic) 1 else 0)
    }

    private fun bindImage(ps: java.sql.PreparedStatement, e: com.yangzhiguo.mushroom.data.local.SpeciesImageEntity) {
        ps.setInt(1, e.speciesId)
        ps.setString(2, e.ufId)
        ps.setString(3, e.ufName)
        ps.setString(4, e.ufSrc)
        val sizeVal = e.ufSize
        if (sizeVal != null) ps.setLong(5, sizeVal) else ps.setNull(5, java.sql.Types.INTEGER)
        ps.setString(6, e.ident)
        ps.setString(7, e.source)
        ps.setInt(8, e.sortOrder)
        ps.setString(9, e.localPath)
    }

    private fun bindDistribution(ps: java.sql.PreparedStatement, e: com.yangzhiguo.mushroom.data.local.DistributionPointEntity) {
        ps.setInt(1, e.speciesId)
        ps.setDouble(2, e.lng)
        ps.setDouble(3, e.lat)
        ps.setString(4, e.value)
        ps.setString(5, e.province)
        ps.setInt(6, e.count)
    }
}

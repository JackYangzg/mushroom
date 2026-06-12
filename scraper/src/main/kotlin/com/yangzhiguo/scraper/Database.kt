package com.yangzhiguo.scraper

import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Types

/**
 * SQLite 持久化层。Xerial JDBC 直接操作 Standard SQLite 文件，
 * 与 Android Room 完全兼容（Room 底层就是 SQLite）。
 *
 * 仅持久化蘑菇元数据 + source_url；
 * 图片相关的字段（sysFileList / kibSpeciesPictures）已从 Specimen 移除，
 * 抓取到的链接以 source_url 形式保留供反查。
 */
class Database(private val dbPath: Path) : AutoCloseable {
    private val log = LoggerFactory.getLogger(Database::class.java)
    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:$dbPath").also {
        // 注意：PRAGMA journal_mode 必须在 autoCommit=true 时设置；切到 WAL 后再开始事务
        it.autoCommit = true
        it.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = ON")
            st.execute("PRAGMA journal_mode = WAL")
            st.execute("PRAGMA synchronous = NORMAL")
        }
        it.autoCommit = false
    }

    init {
        val schema = Database::class.java.classLoader.getResourceAsStream("schema.sql")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("schema.sql not found on classpath")
        val alreadyExists = conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='mushroom_specimen'"
            ).use { rs -> rs.next() && rs.getInt(1) > 0 }
        }
        conn.createStatement().use { st ->
            if (alreadyExists) {
                log.info("Schema already present at $dbPath — skipping CREATE")
            } else {
                st.executeUpdate(schema)
                log.info("Schema created at $dbPath")
            }
        }
        conn.commit()
    }

    fun upsertSpecimen(s: Specimen, sourceUrl: String) {
        conn.prepareStatement(
            """
            INSERT OR REPLACE INTO mushroom_specimen (
                id,
                specimen_describe,
                specimen_group,
                collect_user,
                collect_unit,
                research_team,
                resource_type,
                gather_num,
                collection_num,
                collect_time,
                collect_country,
                collect_province,
                collect_city,
                collect_district,
                collect_village,
                latitude,
                longitude,
                altitude,
                specimen_habitat,
                field_note,
                species_common,
                species_latin,
                field_identification,
                notes,
                is_southwest,
                is_xizang,
                is_sichuan,
                is_guizhou,
                is_gaoligong,
                is_yunnan,
                photo_num,
                create_time,
                community_english,
                community_chinese,
                phylum_english,
                phylum_chinese,
                class_english,
                class_chinese,
                order_english,
                order_chinese,
                suborder_english,
                suborder_chinese,
                family_english,
                family_chinese,
                subfamily_english,
                subfamily_chinese,
                genus_english,
                genus_chinese,
                subgenus_english,
                subgenus_chinese,
                section_english,
                section_chinese,
                species_latin_genus,
                specific_epithet,
                famous_person,
                species_chinese,
                species_description,
                species_habitat,
                description_reference,
                southwest_specific,
                yunnan_specific,
                tropical_species,
                subtropical_species,
                temperate_species,
                edible_fungus,
                medicinal_fungus,
                toxic_fungus,
                mycorrhizal_fungus,
                saprophytic_fungus,
                parasitic_fungus,
                purpose_references,
                habit_references,
                directory_grade,
                directory_references,
                its_genbank,
                its_genbank_url,
                nrlsu_genbank,
                nrlsu_genbank_url,
                tef1_genbank,
                tef1_genbank_url,
                rpb1_genbank,
                rpb1_genbank_url,
                rpb2_genbank,
                rpb2_genbank_url,
                species_checker,
                tree_species,
                climate_zone,
                cap,
                cap_context,
                lamella,
                stipe,
                stipe_context,
                odor,
                calm_seed,
                other_data,
                is_approve,
                fill_user,
                ssu,
                tub2,
                conditionally_fungus,
                substrate,
                strain_number,
                is_open,
                borrow_status,
                assigning_user,
                assigning_id,
                distribution_location,
                economic_use,
                source_url
            ) VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?
            )
            """.trimIndent()
        ).use { ps ->
            var i = 1
            ps.setLong(i++, s.id)
            setNString(ps, i++, s.specimenDescribe)
            setNString(ps, i++, s.specimenGroup)
            setNString(ps, i++, s.collectUser)
            setNString(ps, i++, s.collectUnit)
            setNString(ps, i++, s.researchTeam)
            setNString(ps, i++, s.resourceType)
            setNString(ps, i++, s.gatherNum)
            setNString(ps, i++, s.collectionNum)
            setNString(ps, i++, s.collectTime)
            setNString(ps, i++, s.collectCountry)
            setNString(ps, i++, s.collectProvince)
            setNString(ps, i++, s.collectCity)
            setNString(ps, i++, s.collectDistrict)
            setNString(ps, i++, s.collectVillage)
            setNString(ps, i++, s.latitude)
            setNString(ps, i++, s.longitude)
            setNString(ps, i++, s.altitude)
            setNString(ps, i++, s.specimenHabitat)
            setNString(ps, i++, s.fieldNote)
            setNString(ps, i++, s.speciesCommon)
            setNString(ps, i++, s.speciesLatin)
            setNString(ps, i++, s.fieldIdentification)
            setNString(ps, i++, s.notes)
            setNString(ps, i++, s.isSouthwest)
            setNString(ps, i++, s.isXizang)
            setNString(ps, i++, s.isSichuan)
            setNString(ps, i++, s.isGuizhou)
            setNString(ps, i++, s.isGaoligong)
            setNString(ps, i++, s.isYunnan)
            setNString(ps, i++, s.photoNum)
            setNString(ps, i++, s.createTime)
            setNString(ps, i++, s.communityEnglish)
            setNString(ps, i++, s.communityChinese)
            setNString(ps, i++, s.phylumEnglish)
            setNString(ps, i++, s.phylumChinese)
            setNString(ps, i++, s.classEnglish)
            setNString(ps, i++, s.classChinese)
            setNString(ps, i++, s.orderEnglish)
            setNString(ps, i++, s.orderChinese)
            setNString(ps, i++, s.suborderEnglish)
            setNString(ps, i++, s.suborderChinese)
            setNString(ps, i++, s.familyEnglish)
            setNString(ps, i++, s.familyChinese)
            setNString(ps, i++, s.subfamilyEnglish)
            setNString(ps, i++, s.subfamilyChinese)
            setNString(ps, i++, s.genusEnglish)
            setNString(ps, i++, s.genusChinese)
            setNString(ps, i++, s.subgenusEnglish)
            setNString(ps, i++, s.subgenusChinese)
            setNString(ps, i++, s.sectionEnglish)
            setNString(ps, i++, s.sectionChinese)
            setNString(ps, i++, s.speciesLatinGenus)
            setNString(ps, i++, s.specificEpithet)
            setNString(ps, i++, s.famousPerson)
            setNString(ps, i++, s.speciesChinese)
            setNString(ps, i++, s.speciesDescription)
            setNString(ps, i++, s.speciesHabitat)
            setNString(ps, i++, s.descriptionReference)
            setNString(ps, i++, s.southwestSpecific)
            setNString(ps, i++, s.yunnanSpecific)
            setNString(ps, i++, s.tropicalSpecies)
            setNString(ps, i++, s.subtropicalSpecies)
            setNString(ps, i++, s.temperateSpecies)
            setNString(ps, i++, s.edibleFungus)
            setNString(ps, i++, s.medicinalFungus)
            setNString(ps, i++, s.toxicFungus)
            setNString(ps, i++, s.mycorrhizalFungus)
            setNString(ps, i++, s.saprophyticFungus)
            setNString(ps, i++, s.parasiticFungus)
            setNString(ps, i++, s.purposeReferences)
            setNString(ps, i++, s.habitReferences)
            setNString(ps, i++, s.directoryGrade)
            setNString(ps, i++, s.directoryReferences)
            setNString(ps, i++, s.itsGenbank)
            setNString(ps, i++, s.itsGenbankUrl)
            setNString(ps, i++, s.nrlsuGenbank)
            setNString(ps, i++, s.nrlsuGenbankUrl)
            setNString(ps, i++, s.tef1Genbank)
            setNString(ps, i++, s.tef1GenbankUrl)
            setNString(ps, i++, s.rpb1Genbank)
            setNString(ps, i++, s.rpb1GenbankUrl)
            setNString(ps, i++, s.rpb2Genbank)
            setNString(ps, i++, s.rpb2GenbankUrl)
            setNString(ps, i++, s.speciesChecker)
            setNString(ps, i++, s.treeSpecies)
            setNString(ps, i++, s.climateZone)
            setNString(ps, i++, s.cap)
            setNString(ps, i++, s.capContext)
            setNString(ps, i++, s.lamella)
            setNString(ps, i++, s.stipe)
            setNString(ps, i++, s.stipeContext)
            setNString(ps, i++, s.odor)
            setNString(ps, i++, s.calmSeed)
            setNString(ps, i++, s.otherData)
            setNString(ps, i++, s.isApprove)
            setNString(ps, i++, s.fillUser)
            setNString(ps, i++, s.ssu)
            setNString(ps, i++, s.tub2)
            setNString(ps, i++, s.conditionallyFungus)
            setNString(ps, i++, s.substrate)
            setNString(ps, i++, s.strainNumber)
            if (s.isOpen != null) ps.setInt(i++, s.isOpen) else ps.setNull(i++, Types.INTEGER)
            if (s.borrowStatus != null) ps.setInt(i++, s.borrowStatus) else ps.setNull(i++, Types.INTEGER)
            setNString(ps, i++, s.assigningUser)
            setNString(ps, i++, s.assigningId)
            setNString(ps, i++, s.distributionLocation)
            setNString(ps, i++, s.economicUse)
            setNString(ps, i++, sourceUrl)
            ps.executeUpdate()
        }
        conn.commit()
    }

    fun countSpecimens(): Long = conn.createStatement().use {
        it.executeQuery("SELECT COUNT(*) FROM mushroom_specimen").use { rs ->
            rs.next(); rs.getLong(1)
        }
    }

    /** 抽样校验：返回任意 N 条 specimen 的关键字段。 */
    data class Sample(val id: Long, val speciesLatin: String?, val speciesChinese: String?, val sourceUrl: String?)

    fun sample(limit: Int = 5): List<Sample> {
        val out = mutableListOf<Sample>()
        conn.prepareStatement(
            "SELECT id, species_latin, species_chinese, source_url FROM mushroom_specimen ORDER BY id LIMIT ?"
        ).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    out += Sample(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4))
                }
            }
        }
        return out
    }

    override fun close() {
        try {
            conn.createStatement().use { it.execute("PRAGMA wal_checkpoint(TRUNCATE)") }
        } catch (_: Throwable) {
        }
        conn.close()
    }

    private fun setNString(ps: PreparedStatement, idx: Int, v: String?) {
        if (v.isNullOrEmpty()) ps.setNull(idx, Types.VARCHAR) else ps.setString(idx, v)
    }
}

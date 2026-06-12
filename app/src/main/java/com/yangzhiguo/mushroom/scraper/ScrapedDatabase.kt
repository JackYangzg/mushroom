package com.yangzhiguo.mushroom.scraper

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * Android SQLiteDatabase 包装的 scraped DB 写入器。
 *
 * 表结构与 iflora.cn API 一一对应：102 列（id + 100 字段 + source_url）。
 * 用途：把抓取到的全量数据写入 app internal storage，再由 [com.yangzhiguo.mushroom.sync.ScraperToRoomMapper]
 * 映射回现有 Room SpeciesEntity。
 */
class ScrapedDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION,
), AutoCloseable {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SCHEMA_SQL)
        db.execSQL("CREATE INDEX idx_species_latin ON mushroom_specimen(species_latin)")
        db.execSQL("CREATE INDEX idx_species_chinese ON mushroom_specimen(species_chinese)")
        db.execSQL("CREATE INDEX idx_family_zh ON mushroom_specimen(family_chinese)")
        db.execSQL("CREATE INDEX idx_family_la ON mushroom_specimen(family_english)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS mushroom_specimen")
        onCreate(db)
    }

    fun replaceAll(specimens: List<Specimen>, sourceUrl: (Specimen) -> String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM mushroom_specimen")
            val stmt = db.compileStatement(
                """INSERT INTO mushroom_specimen
                  (id, specimen_describe, specimen_group, collect_user, collect_unit, research_team,
                   resource_type, gather_num, collection_num, collect_time, collect_country,
                   collect_province, collect_city, collect_district, collect_village,
                   latitude, longitude, altitude, specimen_habitat, field_note,
                   species_common, species_latin, field_identification, notes,
                   is_southwest, is_xizang, is_sichuan, is_guizhou, is_gaoligong, is_yunnan,
                   photo_num, create_time, community_english, community_chinese,
                   phylum_english, phylum_chinese, class_english, class_chinese,
                   order_english, order_chinese, suborder_english, suborder_chinese,
                   family_english, family_chinese, subfamily_english, subfamily_chinese,
                   genus_english, genus_chinese, subgenus_english, subgenus_chinese,
                   section_english, section_chinese, species_latin_genus, specific_epithet,
                   famous_person, species_chinese, species_description, species_habitat,
                   description_reference, southwest_specific, yunnan_specific, tropical_species,
                   subtropical_species, temperate_species, edible_fungus, medicinal_fungus,
                   toxic_fungus, mycorrhizal_fungus, saprophytic_fungus, parasitic_fungus,
                   purpose_references, habit_references, directory_grade, directory_references,
                   its_genbank, its_genbank_url, nrlsu_genbank, nrlsu_genbank_url,
                   tef1_genbank, tef1_genbank_url, rpb1_genbank, rpb1_genbank_url,
                   rpb2_genbank, rpb2_genbank_url, species_checker, tree_species,
                   climate_zone, cap, cap_context, lamella, stipe, stipe_context,
                   odor, calm_seed, other_data, is_approve, fill_user, ssu, tub2,
                   conditionally_fungus, substrate, strain_number, is_open, borrow_status,
                   assigning_user, assigning_id, distribution_location, economic_use, source_url)
                  VALUES (?,?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?,?,
                   ?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,
                   ?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,
                   ?,?,?,?, ?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,
                   ?,?,?,?, ?,?,?,?,?, ?,?,?,?, ?,?,?,?, ?)""")
            for (s in specimens) {
                bindAll(stmt, s, sourceUrl(s))
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun countSpecimens(): Long {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM mushroom_specimen", null).use { c ->
            return if (c.moveToFirst()) c.getLong(0) else 0L
        }
    }

    override fun close() {
        super.close()
    }

    companion object {
        const val DB_NAME = "scraped_mushroom.db"
        const val DB_VERSION = 1
        private const val TAG = "ScrapedDatabase"

        private const val SCHEMA_SQL = """
            CREATE TABLE IF NOT EXISTS mushroom_specimen (
                id INTEGER PRIMARY KEY,
                specimen_describe TEXT, specimen_group TEXT, collect_user TEXT, collect_unit TEXT, research_team TEXT,
                resource_type TEXT, gather_num TEXT, collection_num TEXT, collect_time TEXT, collect_country TEXT,
                collect_province TEXT, collect_city TEXT, collect_district TEXT, collect_village TEXT,
                latitude TEXT, longitude TEXT, altitude TEXT, specimen_habitat TEXT, field_note TEXT,
                species_common TEXT, species_latin TEXT, field_identification TEXT, notes TEXT,
                is_southwest TEXT, is_xizang TEXT, is_sichuan TEXT, is_guizhou TEXT, is_gaoligong TEXT, is_yunnan TEXT,
                photo_num TEXT, create_time TEXT,
                community_english TEXT, community_chinese TEXT, phylum_english TEXT, phylum_chinese TEXT,
                class_english TEXT, class_chinese TEXT, order_english TEXT, order_chinese TEXT,
                suborder_english TEXT, suborder_chinese TEXT,
                family_english TEXT, family_chinese TEXT, subfamily_english TEXT, subfamily_chinese TEXT,
                genus_english TEXT, genus_chinese TEXT, subgenus_english TEXT, subgenus_chinese TEXT,
                section_english TEXT, section_chinese TEXT, species_latin_genus TEXT, specific_epithet TEXT,
                famous_person TEXT, species_chinese TEXT, species_description TEXT, species_habitat TEXT,
                description_reference TEXT, southwest_specific TEXT, yunnan_specific TEXT,
                tropical_species TEXT, subtropical_species TEXT, temperate_species TEXT,
                edible_fungus TEXT, medicinal_fungus TEXT, toxic_fungus TEXT,
                mycorrhizal_fungus TEXT, saprophytic_fungus TEXT, parasitic_fungus TEXT,
                purpose_references TEXT, habit_references TEXT, directory_grade TEXT, directory_references TEXT,
                its_genbank TEXT, its_genbank_url TEXT, nrlsu_genbank TEXT, nrlsu_genbank_url TEXT,
                tef1_genbank TEXT, tef1_genbank_url TEXT, rpb1_genbank TEXT, rpb1_genbank_url TEXT,
                rpb2_genbank TEXT, rpb2_genbank_url TEXT, species_checker TEXT, tree_species TEXT,
                climate_zone TEXT, cap TEXT, cap_context TEXT, lamella TEXT, stipe TEXT, stipe_context TEXT,
                odor TEXT, calm_seed TEXT, other_data TEXT,
                is_approve TEXT, fill_user TEXT, ssu TEXT, tub2 TEXT, conditionally_fungus TEXT,
                substrate TEXT, strain_number TEXT, is_open INTEGER, borrow_status INTEGER,
                assigning_user TEXT, assigning_id TEXT, distribution_location TEXT, economic_use TEXT,
                source_url TEXT
            )
        """

        private fun bindAll(stmt: android.database.sqlite.SQLiteStatement, s: Specimen, sourceUrl: String) {
            var i = 1
            stmt.bindLong(i++, s.id)
            bindStr(stmt, i++, s.specimenDescribe)
            bindStr(stmt, i++, s.specimenGroup)
            bindStr(stmt, i++, s.collectUser)
            bindStr(stmt, i++, s.collectUnit)
            bindStr(stmt, i++, s.researchTeam)
            bindStr(stmt, i++, s.resourceType)
            bindStr(stmt, i++, s.gatherNum)
            bindStr(stmt, i++, s.collectionNum)
            bindStr(stmt, i++, s.collectTime)
            bindStr(stmt, i++, s.collectCountry)
            bindStr(stmt, i++, s.collectProvince)
            bindStr(stmt, i++, s.collectCity)
            bindStr(stmt, i++, s.collectDistrict)
            bindStr(stmt, i++, s.collectVillage)
            bindStr(stmt, i++, s.latitude)
            bindStr(stmt, i++, s.longitude)
            bindStr(stmt, i++, s.altitude)
            bindStr(stmt, i++, s.specimenHabitat)
            bindStr(stmt, i++, s.fieldNote)
            bindStr(stmt, i++, s.speciesCommon)
            bindStr(stmt, i++, s.speciesLatin)
            bindStr(stmt, i++, s.fieldIdentification)
            bindStr(stmt, i++, s.notes)
            bindStr(stmt, i++, s.isSouthwest)
            bindStr(stmt, i++, s.isXizang)
            bindStr(stmt, i++, s.isSichuan)
            bindStr(stmt, i++, s.isGuizhou)
            bindStr(stmt, i++, s.isGaoligong)
            bindStr(stmt, i++, s.isYunnan)
            bindStr(stmt, i++, s.photoNum)
            bindStr(stmt, i++, s.createTime)
            bindStr(stmt, i++, s.communityEnglish)
            bindStr(stmt, i++, s.communityChinese)
            bindStr(stmt, i++, s.phylumEnglish)
            bindStr(stmt, i++, s.phylumChinese)
            bindStr(stmt, i++, s.classEnglish)
            bindStr(stmt, i++, s.classChinese)
            bindStr(stmt, i++, s.orderEnglish)
            bindStr(stmt, i++, s.orderChinese)
            bindStr(stmt, i++, s.suborderEnglish)
            bindStr(stmt, i++, s.suborderChinese)
            bindStr(stmt, i++, s.familyEnglish)
            bindStr(stmt, i++, s.familyChinese)
            bindStr(stmt, i++, s.subfamilyEnglish)
            bindStr(stmt, i++, s.subfamilyChinese)
            bindStr(stmt, i++, s.genusEnglish)
            bindStr(stmt, i++, s.genusChinese)
            bindStr(stmt, i++, s.subgenusEnglish)
            bindStr(stmt, i++, s.subgenusChinese)
            bindStr(stmt, i++, s.sectionEnglish)
            bindStr(stmt, i++, s.sectionChinese)
            bindStr(stmt, i++, s.speciesLatinGenus)
            bindStr(stmt, i++, s.specificEpithet)
            bindStr(stmt, i++, s.famousPerson)
            bindStr(stmt, i++, s.speciesChinese)
            bindStr(stmt, i++, s.speciesDescription)
            bindStr(stmt, i++, s.speciesHabitat)
            bindStr(stmt, i++, s.descriptionReference)
            bindStr(stmt, i++, s.southwestSpecific)
            bindStr(stmt, i++, s.yunnanSpecific)
            bindStr(stmt, i++, s.tropicalSpecies)
            bindStr(stmt, i++, s.subtropicalSpecies)
            bindStr(stmt, i++, s.temperateSpecies)
            bindStr(stmt, i++, s.edibleFungus)
            bindStr(stmt, i++, s.medicinalFungus)
            bindStr(stmt, i++, s.toxicFungus)
            bindStr(stmt, i++, s.mycorrhizalFungus)
            bindStr(stmt, i++, s.saprophyticFungus)
            bindStr(stmt, i++, s.parasiticFungus)
            bindStr(stmt, i++, s.purposeReferences)
            bindStr(stmt, i++, s.habitReferences)
            bindStr(stmt, i++, s.directoryGrade)
            bindStr(stmt, i++, s.directoryReferences)
            bindStr(stmt, i++, s.itsGenbank)
            bindStr(stmt, i++, s.itsGenbankUrl)
            bindStr(stmt, i++, s.nrlsuGenbank)
            bindStr(stmt, i++, s.nrlsuGenbankUrl)
            bindStr(stmt, i++, s.tef1Genbank)
            bindStr(stmt, i++, s.tef1GenbankUrl)
            bindStr(stmt, i++, s.rpb1Genbank)
            bindStr(stmt, i++, s.rpb1GenbankUrl)
            bindStr(stmt, i++, s.rpb2Genbank)
            bindStr(stmt, i++, s.rpb2GenbankUrl)
            bindStr(stmt, i++, s.speciesChecker)
            bindStr(stmt, i++, s.treeSpecies)
            bindStr(stmt, i++, s.climateZone)
            bindStr(stmt, i++, s.cap)
            bindStr(stmt, i++, s.capContext)
            bindStr(stmt, i++, s.lamella)
            bindStr(stmt, i++, s.stipe)
            bindStr(stmt, i++, s.stipeContext)
            bindStr(stmt, i++, s.odor)
            bindStr(stmt, i++, s.calmSeed)
            bindStr(stmt, i++, s.otherData)
            bindStr(stmt, i++, s.isApprove)
            bindStr(stmt, i++, s.fillUser)
            bindStr(stmt, i++, s.ssu)
            bindStr(stmt, i++, s.tub2)
            bindStr(stmt, i++, s.conditionallyFungus)
            bindStr(stmt, i++, s.substrate)
            bindStr(stmt, i++, s.strainNumber)
            if (s.isOpen != null) stmt.bindLong(i++, s.isOpen.toLong()) else stmt.bindNull(i++)
            if (s.borrowStatus != null) stmt.bindLong(i++, s.borrowStatus.toLong()) else stmt.bindNull(i++)
            bindStr(stmt, i++, s.assigningUser)
            bindStr(stmt, i++, s.assigningId)
            bindStr(stmt, i++, s.distributionLocation)
            bindStr(stmt, i++, s.economicUse)
            bindStr(stmt, i++, sourceUrl)
            bindStr(stmt, i++, com.yangzhiguo.mushroom.sync.ScraperToRoomMapper.extractPrimaryImageUrl(s))
        }

        private fun bindStr(stmt: android.database.sqlite.SQLiteStatement, idx: Int, v: String?) {
            if (v.isNullOrEmpty()) stmt.bindNull(idx) else stmt.bindString(idx, v)
        }
    }
}

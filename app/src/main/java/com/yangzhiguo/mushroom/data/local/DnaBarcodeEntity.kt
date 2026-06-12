package com.yangzhiguo.mushroom.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * DNA 条形码 (N:1 of [SpecimenEntity]).
 *
 * 7 个基因位点 (ITS / nrLSU / TEF1 / RPB1 / RPB2 / SSU / tub2) 各占一行;
 * (specimenId, gene) 联合主键。每个位点可有:accession (GenBank 编号)、
 * url (BLAST/详情链接)、filename (上传的 FASTA/序列文件)、isPublic
 * (false = 文本 == "数据暂未公开")。
 */
@Entity(
    tableName = "mushroom_dna_barcode",
    primaryKeys = ["specimen_id", "gene"],
    foreignKeys = [ForeignKey(
        entity = SpecimenEntity::class,
        parentColumns = ["id"],
        childColumns = ["specimen_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("specimen_id"),
        Index("gene"),
        Index("accession"),
        Index("species_id"),
    ],
)
data class DnaBarcodeEntity(
    @ColumnInfo("specimen_id") val specimenId: Long,
    @ColumnInfo("species_id") val speciesId: Int,
    @ColumnInfo("gene") val gene: String,
    @ColumnInfo("accession") val accession: String? = null,
    @ColumnInfo("url") val url: String? = null,
    @ColumnInfo("filename") val filename: String? = null,
    @ColumnInfo("is_public") val isPublic: Boolean = true,
) {
    companion object {
        const val GENE_ITS = "ITS"
        const val GENE_NRLSU = "nrLSU"
        const val GENE_TEF1 = "TEF1"
        const val GENE_RPB1 = "RPB1"
        const val GENE_RPB2 = "RPB2"
        const val GENE_SSU = "SSU"
        const val GENE_TUB2 = "tub2"

        val ALL_GENES = listOf(GENE_ITS, GENE_NRLSU, GENE_TEF1, GENE_RPB1, GENE_RPB2, GENE_SSU, GENE_TUB2)
    }
}

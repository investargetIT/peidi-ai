package com.cyanrocks.ai.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @Author wjq
 * @Date 2025/10/30 14:30
 */
@Entity
@Table(name = "ai_milvus_pdf_markdown")
@Data
@JsonIgnoreProperties({"handler", "fieldHandler"})
public class AiMilvusPdfMarkdown {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "milvus_id")
    private String milvusId;

    @Column(name = "title")
    private String title;

    @Lob
    @Column(name = "text", columnDefinition = "mediumtext")
    private String text;

    @Column(name = "report_type")
    private String reportType;

    @Column(name = "expire_date")
    private LocalDate expireDate;


    @Column(name = "report_date")
    private LocalDate reportDate;

    @Column(name = "source")
    private String source;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "report_id")
    private String reportId;

    @Column(name = "source_system")
    private String sourceSystem;

    @Column(name = "lang")
    private String lang;

    @Column(name = "ingest_date")
    private LocalDateTime ingestDate;

    @Column(name = "version")
    private String version;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "visibility")
    private String visibility;

    @Column(name = "tags")
    private String tags;

    @Column(name = "standard_refs")
    private String standardRefs;

    @Column(name = "doc_status")
    private String docStatus;

    @Column(name = "brand")
    private String brand;

    @Column(name = "sku")
    private String sku;

    @Column(name = "spec")
    private String spec;

    @Column(name = "batch_no")
    private Integer batchNo;

    @Column(name = "metedate")
    private String metedate;

    @Column(name = "create_at")
    private LocalDateTime createAt;

    @Column(name = "update_at")
    private LocalDateTime updateAt;

    @Transient
    @TableField(exist = false)
    private List<AiMilvusPdfMarkdown> markdownList;

    @Transient
    @TableField(exist = false)
    private List<Float> vector;

}

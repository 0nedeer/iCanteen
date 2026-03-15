package com.iCanteen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 已清理乱码注释
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_window")
public class Window implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 已清理乱码注释
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 已清理乱码注释
     */
    private Long canteenId;

    /**
     * 已清理乱码注释
     */
    private String name;

    /**
     * 已清理乱码注释
     */
    private String description;

    /**
     * 已清理乱码注释
     */
    private Integer waitTime;

    /**
     * 已清理乱码注释
     */
    private Integer waitTimeLevel;

    /**
     * 已清理乱码注释
     */
    private Integer status;

    /**
     * 已清理乱码注释
     */
    private Integer sort;

    /**
     * 已清理乱码注释
     */
    private LocalDateTime createTime;

    /**
     * 已清理乱码注释
     */
    private LocalDateTime updateTime;
}



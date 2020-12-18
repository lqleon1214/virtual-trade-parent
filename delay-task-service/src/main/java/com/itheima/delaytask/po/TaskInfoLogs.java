package com.itheima.delaytask.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * Created by 传智播客*黑马程序员.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("taskinfo_logs")
public class TaskInfoLogs extends TaskInfo implements Serializable {

    /**
     *  `version` int(11) NOT NULL COMMENT '版本号,用乐观锁',
     `status` int(11) DEFAULT '0' COMMENT '状态 0=初始化状态 1=EXECUTED 2=CANCELLED',
     */
    @Version
    private Integer version;

    @TableField
    private Integer status;
}

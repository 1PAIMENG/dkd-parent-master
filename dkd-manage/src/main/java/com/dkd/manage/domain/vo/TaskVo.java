package com.dkd.manage.domain.vo;

import com.dkd.manage.domain.Task;
import com.dkd.manage.domain.TaskType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.Map;

@Data
public class TaskVo extends Task {
    //注意 Task 中有继承的Params属性
    /**
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> params;
     */
    //工单类型
    private TaskType taskType;
}

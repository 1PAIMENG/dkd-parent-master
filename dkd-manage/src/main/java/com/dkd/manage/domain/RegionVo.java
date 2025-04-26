package com.dkd.manage.domain;

import lombok.Data;

//返回给前端的实体类
@Data
public class RegionVo extends Region{
    //点位数量
    private Integer nodeCount;

}

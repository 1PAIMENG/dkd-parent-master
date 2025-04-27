package com.dkd.manage.domain;

import lombok.Data;

@Data
public class NodeVo extends Node{
    // 设备数量
    private Integer vmCount;
    //区域信息
    private Region region;
    //合作商信息
    private Partner partner;
}

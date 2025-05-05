package com.dkd.manage.service.impl;

import java.util.ArrayList;
import java.util.List;

import com.dkd.common.constant.DkdContants;
import com.dkd.common.utils.DateUtils;
import com.dkd.common.utils.bean.BeanUtils;
import com.dkd.common.utils.uuid.UUIDUtils;
import com.dkd.manage.domain.Channel;
import com.dkd.manage.domain.Node;
import com.dkd.manage.domain.VmType;
import com.dkd.manage.service.IChannelService;
import com.dkd.manage.service.INodeService;
import com.dkd.manage.service.IVmTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.dkd.manage.mapper.VendingMachineMapper;
import com.dkd.manage.domain.VendingMachine;
import com.dkd.manage.service.IVendingMachineService;
import org.springframework.transaction.annotation.Transactional;

/**
 * 设备管理Service业务层处理
 * 
 * @author paibumeng
 * @date 2025-05-04
 */
@Service
public class VendingMachineServiceImpl implements IVendingMachineService 
{
    @Autowired
    private VendingMachineMapper vendingMachineMapper;

    @Autowired
    private IVmTypeService vmTypeService;

    @Autowired
    private INodeService nodeService;

    @Autowired
    private IChannelService  channelService;

    /**
     * 查询设备管理
     * 
     * @param id 设备管理主键
     * @return 设备管理
     */
    @Override
    public VendingMachine selectVendingMachineById(Long id)
    {
        return vendingMachineMapper.selectVendingMachineById(id);
    }

    /**
     * 查询设备管理列表
     * 
     * @param vendingMachine 设备管理
     * @return 设备管理
     */
    @Override
    public List<VendingMachine> selectVendingMachineList(VendingMachine vendingMachine)
    {
        return vendingMachineMapper.selectVendingMachineList(vendingMachine);
    }

    /**
     * 新增设备管理
     * 
     * @param vendingMachine 设备管理
     * @return 结果
     */
    @Transactional
    @Override
    public int insertVendingMachine(VendingMachine vendingMachine)
    {
        //1.新增设备
        //1-1 生成8位的唯一表示inner-code
        String innerCode = UUIDUtils.getUUID();
        vendingMachine.setInnerCode(innerCode);
        //1-2 查询售货机类型表
        VmType vmType =vmTypeService.selectVmTypeById(vendingMachine.getVmTypeId());
        vendingMachine.setChannelMaxCapacity(vmType.getChannelMaxCapacity());
        //1-3 提交点位id相关信息 区域 点位 合作商 商圈类型
        Node node =nodeService.selectNodeById(vendingMachine.getNodeId());
        //使用工具类注入属性   减少操作
        BeanUtils.copyProperties(node,vendingMachine,"id");
        vendingMachine.setAddr(node.getAddress());
        //1-4 设备状态
        //vendingMachine.setVmStatus(0L);//表示未投放
        vendingMachine.setVmStatus(DkdContants.VM_STATUS_NODEPLOY);//表示未投放
        vendingMachine.setCreateTime(DateUtils.getNowDate());
        vendingMachine.setUpdateTime(DateUtils.getNowDate());
        //1-5 保存
        int result = vendingMachineMapper.insertVendingMachine(vendingMachine);
        //2.新增货道
        //2--1
        List<Channel> channelList = new ArrayList<>();
        for (int i=1;i<=vmType.getVmRow();i++){
            for (int j=1;j<=vmType.getVmCol();j++){
                //封装channel对象
                Channel channel = new Channel();
                channel.setChannelCode(i+"-"+j);//货道编号
                channel.setVmId(vendingMachine.getId());//售货机id
                channel.setInnerCode(vendingMachine.getInnerCode());//售货机编号
                channel.setMaxCapacity(vmType.getChannelMaxCapacity());//货道最大容量
                channel.setCreateTime(DateUtils.getNowDate());
                channel.setUpdateTime(DateUtils.getNowDate());
                channelList.add(channel);
            }
        }
        //2--2 批量保存
        channelService.batchInsertChannel(channelList);

        return result;
    }

    /**
     * 修改设备管理
     * 
     * @param vendingMachine 设备管理
     * @return 结果
     */
    @Override
    public int updateVendingMachine(VendingMachine vendingMachine)
    {
        vendingMachine.setUpdateTime(DateUtils.getNowDate());
        return vendingMachineMapper.updateVendingMachine(vendingMachine);
    }

    /**
     * 批量删除设备管理
     * 
     * @param ids 需要删除的设备管理主键
     * @return 结果
     */
    @Override
    public int deleteVendingMachineByIds(Long[] ids)
    {
        return vendingMachineMapper.deleteVendingMachineByIds(ids);
    }

    /**
     * 删除设备管理信息
     * 
     * @param id 设备管理主键
     * @return 结果
     */
    @Override
    public int deleteVendingMachineById(Long id)
    {
        return vendingMachineMapper.deleteVendingMachineById(id);
    }
}

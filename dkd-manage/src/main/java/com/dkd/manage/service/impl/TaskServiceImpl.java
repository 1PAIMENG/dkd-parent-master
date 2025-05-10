package com.dkd.manage.service.impl;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.dkd.common.constant.DkdContants;
import com.dkd.common.exception.ServiceException;
import com.dkd.common.utils.DateUtils;
import com.dkd.manage.domain.Emp;
import com.dkd.manage.domain.TaskDetails;
import com.dkd.manage.domain.VendingMachine;
import com.dkd.manage.domain.dto.TaskDetailsDto;
import com.dkd.manage.domain.dto.TaskDto;
import com.dkd.manage.domain.vo.TaskVo;
import com.dkd.manage.service.IEmpService;
import com.dkd.manage.service.ITaskDetailsService;
import com.dkd.manage.service.IVendingMachineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.dkd.manage.mapper.TaskMapper;
import com.dkd.manage.domain.Task;
import com.dkd.manage.service.ITaskService;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工单Service业务层处理
 * 
 * @author paibumeng
 * @date 2025-05-09
 */
@Service
public class TaskServiceImpl implements ITaskService 
{
    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private IVendingMachineService vendingMachineService;

    @Autowired
    private IEmpService empService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ITaskDetailsService taskDetailsService;

    /**
     * 查询工单
     * 
     * @param taskId 工单主键
     * @return 工单
     */
    @Override
    public Task selectTaskByTaskId(Long taskId)
    {
        return taskMapper.selectTaskByTaskId(taskId);
    }

    /**
     * 查询工单列表
     * 
     * @param task 工单
     * @return 工单
     */
    @Override
    public List<Task> selectTaskList(Task task)
    {
        return taskMapper.selectTaskList(task);
    }

    /**
     * 新增工单
     * 
     * @param task 工单
     * @return 结果
     */
    @Override
    public int insertTask(Task task)
    {
        task.setCreateTime(DateUtils.getNowDate());
        return taskMapper.insertTask(task);
    }

    /**
     * 修改工单
     * 
     * @param task 工单
     * @return 结果
     */
    @Override
    public int updateTask(Task task)
    {
        task.setUpdateTime(DateUtils.getNowDate());
        return taskMapper.updateTask(task);
    }

    /**
     * 批量删除工单
     * 
     * @param taskIds 需要删除的工单主键
     * @return 结果
     */
    @Override
    public int deleteTaskByTaskIds(Long[] taskIds)
    {
        return taskMapper.deleteTaskByTaskIds(taskIds);
    }

    /**
     * 删除工单信息
     * 
     * @param taskId 工单主键
     * @return 结果
     */
    @Override
    public int deleteTaskByTaskId(Long taskId)
    {
        return taskMapper.deleteTaskByTaskId(taskId);
    }

    /**
     * 查询工单列表
     *
     * @param task 工单
     * @return 工单TaskVo集合
     */
    @Override
    public List<TaskVo> selectTaskVoList(Task task) {
        return taskMapper.selectTaskVoList(task);
    }

    /**
     * 新增运营 运维工单
     * @param taskDto
     * @return 结果
     */
    @Transactional
    @Override
    public int insetTaskDto(TaskDto taskDto) {
        System.out.println(taskDto);
        //查询售货机id是否存在
        VendingMachine vendingMachine = vendingMachineService.selectVendingMachineByInnerCode(taskDto.getInnerCode());
        if (vendingMachine == null) {
            throw new ServiceException("售货机不存在");
        }
        // 2. 校验售货机状态与工单类型是否相符
        checkCreateTask(vendingMachine.getVmStatus(),taskDto.getProductTypeId());
        //3.检查是否有同类型未完成的工单
        hasTask(taskDto);
        //4.查询校验员工是否存在
        Emp emp = empService.selectEmpById(taskDto.getUserId());
        if (emp == null) {
            throw new ServiceException("员工不存在");
        }
        //5.校验员工区域是否匹配
        if (!emp.getRegionId().equals(vendingMachine.getRegionId())) {
            throw new ServiceException("员工区域与售货机区域不匹配");
        }
        //1-5 是为了防止前端篡改提交的数据
        //6.将dto转换为task对象
        Task task= BeanUtil.copyProperties(taskDto,Task.class);//属性复制
        task.setTaskStatus(DkdContants.TASK_STATUS_CREATE);//工单状态为创建
        task.setUserName(emp.getUserName());//执行人名称
        task.setRegionId(vendingMachine.getRegionId());//执行人区域
        task.setAddr(vendingMachine.getAddr());//执行人地址
        task.setCreateTime(DateUtils.getNowDate());//创建时间
        //生成并获取当天的工单编号
        task.setTaskCode(generateTaskCode());//工单编号
        int taskResult = taskMapper.insertTask(task);
        //7.如果是补货工单，则插入工单详情
        if (taskDto.getProductTypeId().equals(DkdContants.TASK_TYPE_SUPPLY)){
            List<TaskDetailsDto> details = taskDto.getDetails();
//            System.out.println("------------------------------------------");
//            System.out.println(details);
            if(CollUtil.isEmpty(details)){
                throw new ServiceException("补货工单详情不能为空");
            }
            //将details中的数据转换为taskDetails对象
            List<TaskDetails> taskDetailsList =details.stream().map(dto -> {
                TaskDetails taskDetails=  BeanUtil.copyProperties(dto, TaskDetails.class);
                taskDetails.setTaskId(task.getTaskId());
                return taskDetails;
            }).collect((Collectors.toList()));


           //批量新增
            taskDetailsService.batchInsertTaskDetails(taskDetailsList);
        }
        return taskResult;
    }

    /**
     * 取消工单
     * @param task
     * @return 结果
     */
    @Override
    public int cancelTask(Task task) {
        //判断工单状态是否可以取消
        Task taskDb = taskMapper.selectTaskByTaskId(task.getTaskId());
        if (taskDb.getTaskStatus().equals(DkdContants.TASK_STATUS_CANCEL)){
            throw new ServiceException("该工单已取消,不能再次取消");
        }
        //判断工单状态是否为已完成
        if (taskDb.getTaskStatus().equals(DkdContants.TASK_STATUS_FINISH)){
            throw new ServiceException("该工单已完成,不能取消");
        }
        task.setUpdateTime(DateUtils.getNowDate());
        task.setTaskStatus(DkdContants.TASK_STATUS_CANCEL);
        return taskMapper.updateTask(task);//task 当中含有desc备注说明
    }

    //生成并获取当天的工单编号
    private String generateTaskCode(){
        //获取当前日期并格式化为yyyymmdd
        String dateStr = DateUtils.getDate().replaceAll("-","");
        //根据日期生成redis的键
        String key = "dkd.task.code." + dateStr;
        //判断key是否存在
        if (!redisTemplate.hasKey(key)) {
            //如果不存在，则初始化为1，并制定过期时间为1天
            redisTemplate.opsForValue().set(key,1,Duration.ofDays(1));
            return dateStr + "0001";
        }
        //如果存在，则获取当前值，并加1，然后更新到redis中
        return dateStr+StrUtil.padPre(redisTemplate.opsForValue().increment(key).toString(),4, "0");
    }
    // 检查设备是否有未完成的同类型工单
    private void hasTask(TaskDto taskDto) {
        // 创建task条件对象，并设置设备编号和工单类型，以及工单状态为进行中
        Task taskParam = new Task();
        taskParam.setInnerCode(taskDto.getInnerCode());
        taskParam.setProductTypeId(taskDto.getProductTypeId());
        taskParam.setTaskStatus(DkdContants.TASK_STATUS_PROGRESS);

        // 调用taskMapper查询数据库查看是否有符合条件的工单列表
        List<Task> taskList = taskMapper.selectTaskList(taskParam);
        // 如果存在未完成的同类型工单，抛出异常
        if (!taskList.isEmpty()) {
            throw new RuntimeException("设备已有未完成的同类型工单");
        }
    }

    private void checkCreateTask(Long vmStatus,Long prpductTypeId){
        // 如果是投放工单，设备在运行中，抛出异常
        if (prpductTypeId == DkdContants.TASK_TYPE_DEPLOY && vmStatus == DkdContants.VM_STATUS_RUNNING) {
            throw new ServiceException("该设备状态为运行中，无法进行投放");
        }
        // 如果是维修工单，设备不在运行中，抛出异常
        if (prpductTypeId == DkdContants.TASK_TYPE_REPAIR && vmStatus != DkdContants.VM_STATUS_RUNNING) {
            throw new ServiceException("该设备状态不为运行中，无法进行投放");
        }
        // 如果是补货工单，设备不在运行中，抛出异常
        if (prpductTypeId == DkdContants.TASK_TYPE_SUPPLY && vmStatus != DkdContants.VM_STATUS_RUNNING) {
            throw new ServiceException("该设备状态不为运行中，无法进行投放");
        }
        // 如果是撤机工单，设备不在运行中，抛出异常
        if (prpductTypeId == DkdContants.TASK_TYPE_REVOKE&& vmStatus != DkdContants.VM_STATUS_RUNNING) {
            throw new ServiceException("该设备状态不为运行中，无法进行投放");
        }
    }
}

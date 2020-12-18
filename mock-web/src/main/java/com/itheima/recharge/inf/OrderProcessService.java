package com.itheima.recharge.inf;

import com.itheima.recharge.RechargeRequest;
import com.itheima.recharge.RechargeResponse;
import com.itheima.recharge.entity.OrderTrade;
import com.itheima.response.Result;

import java.util.List;

/**
 * 订单系统集成
 */
public interface OrderProcessService {
	/**
	 * 充值对接订单系统
	 * @return
	 * @throws Exception
	 */
	Result<RechargeResponse> recharge(RechargeRequest request) throws Exception;
	
	/**
	 * 获取订单信息
	 * @param orderNo
	 * @return
	 * @throws Exception
	 */
	OrderTrade queryOrderByNo(String orderNo) throws Exception;
	
	
	/**
	 * 获取所有订单
	 * @return
	 * @throws Exception
	 */
	List<OrderTrade> queryAllOrder() throws Exception;
	
	
    void removeOrderTrade(String orderNo);
}

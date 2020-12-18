package com.itheima.recharge.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.itheima.recharge.OrderStatusEnum;
import com.itheima.recharge.RechargeResponse;
import com.itheima.recharge.constants.SupplierConstants;
import com.itheima.recharge.mock.CacheService;
import com.itheima.response.Result;
import com.itheima.thirdparty.StatusCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * 极速充值mock
 */
@RestController
@RequestMapping("/jisuapi")
public class MockJisuRechargeController extends BaseController{
	
	@Autowired
	RestTemplate restTemplate;

	@Autowired
	CacheService cacheService;

	/**
	 *
	 * @param mobile
	 * @param amount
	 * @param outorderNo
	 * @param reqStatus  可以自定义成功失败
	 * @param orderStatus  可以自定义回调订单状态
	 * @return
	 */
	@RequestMapping(value = "/mobilerecharge/recharge")
	public Result<RechargeResponse> add(String mobile, String amount, String outorderNo, @RequestParam(value="req_status",defaultValue="200",required=false) int reqStatus, @RequestParam(value="order_status",defaultValue="2",required=false) int orderStatus) {
		Result<RechargeResponse> result = new Result<RechargeResponse>();
		RechargeResponse response = new RechargeResponse();
		response.setMoblie(mobile);
		response.setOrderNo(outorderNo);
		String tradeNo = UUID.randomUUID().toString().replace("-","").toLowerCase();//IdWorker.get32UUID();
		System.out.println("tradeNo "+tradeNo);
		response.setTradeNo(tradeNo);
		response.setPamt(Double.valueOf(amount));
		result.setData(response);
		// 充值请求成功
		if(reqStatus== StatusCode.OK) {
			result.setCode(StatusCode.OK);
			//实际充值 结果异步通知
			rechargeNotify(outorderNo,tradeNo,orderStatus);
			//模拟供应商订单保存
			response.setStatus(orderStatus);
			cacheService.hPut(SupplierConstants.jisu_order,outorderNo+tradeNo, JSON.toJSONString(result));
		}else if(reqStatus==StatusCode.ERROR){
			// 充值请求失败
			result.setCode(StatusCode.ORDER_REQ_FAILED);
			result.setMsg("请求充值失败，请重试");
		}
		return result;
	}

	@RequestMapping(value = "/mobilerecharge/orderState")
	public Result<RechargeResponse> orderState(String outorderNo,String tradeNo) {
		Result<RechargeResponse> result;
		String key=outorderNo+tradeNo;
		if(cacheSevice.hExists(SupplierConstants.jisu_order, key)) {
			result = (Result<RechargeResponse>) JSON.parseObject(String.valueOf(cacheSevice.hGet(SupplierConstants.jisu_order,key)), new TypeReference<Result<RechargeResponse>>() {});
		}else {
			result = new Result<RechargeResponse>();
			RechargeResponse response = new RechargeResponse();
			response.setStatus(OrderStatusEnum.FAIL.getCode());
			response.setOrderNo(outorderNo);
			result.setCode(StatusCode.ERROR);
			result.setMsg("充值失败");
		}
		return result;
	}





}

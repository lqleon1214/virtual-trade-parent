package com.itheima.recharge.controller;

import com.itheima.recharge.OrderStatusEnum;
import com.itheima.recharge.RechargeRequest;
import com.itheima.recharge.RechargeResponse;
import com.itheima.response.Result;
import com.itheima.thirdparty.StatusCode;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 聚合充值mock
 */
@RestController
@RequestMapping("/juheapi")
public class MockJuheRechargeController extends BaseController {

	@RequestMapping(value = "/recharge")
	public Result<RechargeResponse> add(@RequestBody RechargeRequest request) {
		Result<RechargeResponse> result = new Result<RechargeResponse>();
		RechargeResponse response = new RechargeResponse();
		response.setMoblie(request.getMobile());
		response.setOrderNo(request.getOrderNo());
		response.setTradeNo(UUID.randomUUID().toString().replace("-","").toLowerCase());
		response.setPamt(request.getPamt());
		result.setData(response);
		result.setCode(StatusCode.BALANCE_NOT_ENOUGH);
		result.setMsg("余额不足");
		return result;
	}

	@RequestMapping(value = "/orderState")
	public Result<RechargeResponse> orderState(String outorderNo,String tradeNo) {
		Result<RechargeResponse> result = new Result<RechargeResponse>();
		RechargeResponse response = new RechargeResponse();
		response.setStatus(OrderStatusEnum.FAIL.getCode());
		response.setOrderNo(outorderNo);
		result.setCode(StatusCode.ERROR);
		result.setMsg("充值失败");
		return  result;
	}

}

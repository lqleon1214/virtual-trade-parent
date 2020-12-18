package com.itheima.supplier.rabbitListener;

import com.itheima.recharge.RechargeRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Created by 传智播客*黑马程序员.
 */
@Component
@Slf4j
public class PaySuccessListener {


    /**
     * 监听消息:
     * @param rechargeRequest
     */
    @RabbitListener(queues = {"pay"})
    public void onMessage(RechargeRequest rechargeRequest) {
        log.info("RabbitListener 监听到了消息,{}",rechargeRequest);
    }
}

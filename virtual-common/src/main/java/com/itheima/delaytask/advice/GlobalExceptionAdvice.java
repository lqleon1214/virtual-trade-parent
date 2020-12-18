package com.itheima.delaytask.advice;

import com.alibaba.fastjson.JSON;
import com.itheima.delaytask.exception.ScheduleSystemException;
import com.itheima.delaytask.exception.TaskNotExistException;
import com.itheima.response.ResponseMessage;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Created by 传智播客*黑马程序员.
 */
@RestControllerAdvice
public class GlobalExceptionAdvice {
    
    @ResponseBody
    @ExceptionHandler({ScheduleSystemException.class, TaskNotExistException.class})
    public ResponseMessage delayTaskException(RuntimeException e){
        return ResponseMessage.error(e);
    }

    @ResponseBody
    @ExceptionHandler({Exception.class})
    public ResponseMessage commonException(RuntimeException e){
        return ResponseMessage.error(e);
    }
    
}

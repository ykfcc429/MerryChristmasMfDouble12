package com.example.demo.aop;

import com.alibaba.fastjson.JSON;
import com.example.demo.pojo.User;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;

@Component
@Aspect
public class RequestFilter {

    @Autowired
    private RedisTemplate<String ,String> redisTemplate;

    //这一块是个简单的网关
    @Around("@annotation(com.example.demo.aop.Filter)")
    public Object aroud(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        Object[] args = proceedingJoinPoint.getArgs();
        ModelAndView modelAndView = new ModelAndView();
        for (Object arg : args) {
            if(arg instanceof ModelAndView){
                modelAndView = (ModelAndView) arg;
                break;
            }
        }
        for (Object arg : args) {
            if(arg instanceof HttpServletRequest){
                HttpServletRequest request = (HttpServletRequest)arg;
                Object token = request.getSession().getAttribute("token");
                if(token == null){
                    modelAndView.setViewName("login");
                    return modelAndView;
                }
                String s = redisTemplate.opsForValue().get(token);
                User user = JSON.parseObject(s,User.class);
                if(user == null){
                    modelAndView.setViewName("login");
                    return modelAndView;
                }
            }
        }
        return proceedingJoinPoint.proceed();
    }
}

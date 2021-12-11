package com.example.demo.control;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.demo.aop.Filter;
import com.example.demo.pojo.Gifts;
import com.example.demo.pojo.User;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;

import static com.example.demo.util.CommonUtil.setUserSession;

@Controller
@RequestMapping
public class IndexController {

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private RedisTemplate<String,String > redisTemplate;

    @RequestMapping({"index",""})
    @Filter
    public ModelAndView index(ModelAndView model, HttpServletRequest request) {
        User userFromSession = getUser(request);
        if(userFromSession == null){
            model.setViewName("login");
            return model;
        }
        model.addObject("user",userFromSession);
        model.setViewName("index");
        return model;
    }

    @RequestMapping(value = "login")
    public ModelAndView login(ModelAndView modelAndView,String token,HttpServletRequest request){
        setUserSession(request,token);
        User user = getUser(request);
        if(user == null){
            modelAndView.setViewName("login");
            return modelAndView;
        }
        modelAndView.setViewName("redirect:index");
        return modelAndView;
    }

    @RequestMapping("register")
    public ModelAndView register(ModelAndView model,String token,HttpServletRequest request)throws IOException{
        if(!StringUtils.hasLength(token)){
            model.setViewName("register");
            return model;
        }
        if(!redisTemplate.keys(token).isEmpty()){
            model.setViewName("redirect:index");
            return model;
        }
        OkHttpClient okHttpClient = new OkHttpClient();
        Response execute = okHttpClient.newCall(new Request.Builder().url("http:\\\\localhost:8080/name/randomName").build()).execute();
        assert execute.body() != null;
        String string = execute.body().string();
        JSONObject jsonObject = JSON.parseObject(string);
        String name = "U Don't Need A Name";
        if(jsonObject.containsKey("message")){
            name = jsonObject.getString("message");
        }
        User user = new User(name,0);
        String userStr = JSON.toJSONString(user);
        model.addObject("user",user);
        redisTemplate.opsForValue().set(token,userStr);
        setUserSession(request,token);
        model.setViewName("redirect:index");
        return model;
    }

    @RequestMapping("logOut")
    public ModelAndView logOut(ModelAndView modelAndView,HttpServletRequest request){
        request.getSession().removeAttribute("token");
        modelAndView.setViewName("redirect:index");
        return modelAndView;
    }

    @RequestMapping("upLoad")
    @Filter
    public ModelAndView upLoad(ModelAndView modelAndView,HttpServletRequest request){
        modelAndView.setViewName("upLoadGift");
        return modelAndView;
    }

    @RequestMapping("upLoadGift")
    @Filter
    public ModelAndView upLoadGift(ModelAndView modelAndView, HttpServletRequest request, Gifts gifts){
        modelAndView.setViewName("status");
        if(StringUtils.hasLength(gifts.getGift1())||StringUtils.hasLength(gifts.getGift2())||StringUtils.hasLength(gifts.getGift3())){
            String token =(String) request.getSession().getAttribute("token");
            Long members = redisTemplate.opsForSet().size("members");
            if(members!=null && members<5) {
                Boolean isMember = redisTemplate.opsForSet().isMember("members", token);
                if (isMember != null && isMember) {
                    modelAndView.addObject("status", "已更新愿望单");
                } else {
                    modelAndView.addObject("status", "已添加愿望单,等待所有愿望单收集完毕后会有邮件下发");
                    redisTemplate.opsForSet().add("members", token);
                }
                redisTemplate.opsForValue().set("gift_"+token,JSON.toJSONString(gifts));
                Long members1 = redisTemplate.opsForSet().size("members");
                if(members1!=null && members1==5){
                    send();
                }
            }else{
                modelAndView.addObject("status","愿望单已收集完毕");
                return modelAndView;
            }
        }else {
            modelAndView.addObject("status","你确定你啥也不要吗,还是我系统出问题了");
            return modelAndView;
        }
        return modelAndView;
    }

    @RequestMapping("status")
    public ModelAndView status(ModelAndView modelAndView){
        modelAndView.setViewName("status");
        return modelAndView;
    }


    private void send(){
        Set<String> members = redisTemplate.opsForSet().members("members");
        if(members==null){
            return;
        }
        Set<String> consumers = new HashSet<>(members);
        Map<String,String> map = new HashMap<>();
        for (String member : members) {
            boolean flag = true;
            while (flag) {
                String s = redisTemplate.opsForSet().randomMember("members");
                if(!member.equals(s) && consumers.contains(s)){
                    map.put(member,s);
                    consumers.remove(s);
                    flag = false;
                }else {
                    flag = true;
                }
            }
        }
        for (String s : map.keySet()) {
            String gift = redisTemplate.opsForValue().get("gift_" + s);
            Gifts gifts = JSON.parseObject(gift,Gifts.class);
            String s1 = map.get(s);
            assert gifts != null;
            sendEmail(s1,gifts);
        }
    }

    @RequestMapping("sendMail")
    public void sendEmail(String receiver,Gifts gifts){
        // 构建一个邮件对象
        SimpleMailMessage message = new SimpleMailMessage();
        // 设置邮件主题
        message.setSubject("收到愿望单啦!");
        // 设置邮件发送者，这个跟application.yml中设置的要一致
        message.setFrom("1141154320@qq.com");
        // 设置邮件接收者，可以有多个接收者，中间用逗号隔开，以下类似
        message.setTo(receiver);
        // 设置邮件发送日期
        message.setSentDate(new Date());
        // 设置邮件的正文
        message.setText("不知道是谁的单子呢,请大方的选择!"
                +"\n1:"+gifts.getGift1()+",链接:"+gifts.getUrl1()
                +"\n2:"+gifts.getGift2()+";链接:"+gifts.getUrl2()
                +"\n3:"+gifts.getGift3()+";链接:"+gifts.getUrl3()
                +"\n\n\n powered by MerryChristmasMfDouble12 中文译名:圣诞十二响"
        );
        // 发送邮件
        javaMailSender.send(message);
    }

    @RequestMapping("upLoadAesKey")
    @Filter
    public ModelAndView upLoadAesKey(ModelAndView modelAndView,HttpServletRequest request,String aesKey){
        Object token = request.getSession().getAttribute("token");
        String key = "aes_"+token;
        Set<String> uids;
        if(!StringUtils.hasLength(aesKey) || aesKey.getBytes().length!=3){
            modelAndView.addObject("status","aes填写错误");
            return modelAndView;
        }
        if ((uids = redisTemplate.keys("aes*"))==null || uids.isEmpty()) {
            redisTemplate.opsForValue().set(key,aesKey);
        }else {
            if (uids.contains(key)) {
                modelAndView.addObject("status","请勿重复上传aes");
            }else if(uids.size()>=5){
                modelAndView.addObject("status","aes已上传5条");
            }else {
                modelAndView.addObject("status","aes上传成功");
                redisTemplate.opsForValue().set(key,aesKey);
                uids.add(key);
                if(uids.size()==5){
                    List<String > list = new ArrayList<>();
                    for (String uid : uids) {
                        String s = redisTemplate.opsForValue().get(uid);
                        list.add(s);
                    }
                    list.sort(Comparator.reverseOrder());
                    String collect = String.join("", list);
                    collect = collect.concat("a");
                    redisTemplate.opsForValue().set("skeyOfAes",collect);
                }
            }
        }
        return modelAndView;
    }

    public User getUser(HttpServletRequest request){
        String  token = (String)request.getSession().getAttribute("token");
        String s = redisTemplate.opsForValue().get(token);
        if(StringUtils.hasLength(s)){
            return JSON.parseObject(s,User.class);
        }
        return null;
    }


}

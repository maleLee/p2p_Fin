package cn.haiyang.action.user;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.Namespace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.opensymphony.xwork2.ModelDriven;

import cn.haiyang.action.admin.BaseAction;
import cn.haiyang.cache.BaseCacheService;
import cn.haiyang.domain.user.UserModel;
import cn.haiyang.service.user.IUserService;
import cn.haiyang.service.userAccount.IUserAccountService;
import cn.haiyang.utils.ConfigurableConstants;
import cn.haiyang.utils.FrontStatusConstants;
import cn.haiyang.utils.ImageUtil;
import cn.haiyang.utils.Response;
import cn.haiyang.utils.TokenUtil;

@SuppressWarnings("serial")
@Controller
@Scope("prototype")
@Namespace("/user")
public class UserAction extends BaseAction implements ModelDriven<UserModel>{
	
	private UserModel user = new UserModel();
	@Override
	public UserModel getModel() {
		// TODO Auto-generated method stub
		return user;
	}
	@Autowired
	private BaseCacheService baseCache;
	
	@Autowired
	private IUserAccountService accountservice;
	@Autowired
	private IUserService iUserService;
	
	
	/**
	 * 生产令牌
	 * @param userName
	 * @return
	 */
	public String generateUserToken(String userName) {

		try {
			// 生成令牌
			String token = TokenUtil.generateUserToken(userName);

			// 根据用户名获取用户
			UserModel user = iUserService.findByUsername(userName);
			// 将用户信息存储到map中。
			Map<String, Object> tokenMap = new HashMap<String, Object>();
			tokenMap.put("id", user.getId());
			tokenMap.put("userName", user.getUsername());
			tokenMap.put("phone", user.getPhone());
			tokenMap.put("userType", user.getUserType());
			tokenMap.put("payPwdStatus", user.getPayPwdStatus());
			tokenMap.put("emailStatus", user.getEmailStatus());
			tokenMap.put("realName", user.getRealName());
			tokenMap.put("identity", user.getIdentity());
			tokenMap.put("realNameStatus", user.getRealNameStatus());
			tokenMap.put("payPhoneStatus", user.getPhoneStatus());

			baseCache.del(token);
			baseCache.setHmap(token, tokenMap); // 将信息存储到redis中

			// 获取配置文件中用户的生命周期，如果没有，默认是30分钟
			String tokenValid = ConfigurableConstants.getProperty("token.validity", "30");
			tokenValid = tokenValid.trim();
			baseCache.expire(token, Long.valueOf(tokenValid) * 60);

			return token;
		} catch (Exception e) {
			e.printStackTrace();
			return Response.build().setStatus("-9999").toJSON();
		}
	}
	
	/**
	 * 产生uuid,保存到redis中
	 */
	@Action("uuid")
	public void user(){
		String uuid = UUID.randomUUID().toString();
		baseCache.set(uuid, uuid);
		baseCache.expire(uuid, 3*60);
		try {
			this.getResponse().getWriter().write(Response.build().setStatus("1").setUuid(uuid).toJSON());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	/**
	 * 校验验证码
	 */
	@Action("codeValidate")
	public void codeValidate(){
		String signUuid = this.getRequest().getParameter("signUuid");
		String signCode = this.getRequest().getParameter("signCode");
		String _signCode = baseCache.get(signUuid);
		try {
			//录入为空
			if(StringUtils.isEmpty(signCode)){
				this.getResponse().getWriter().write(Response.build().setStatus("1").toJSON());
				return;
			}
			//验证码为空
			if(StringUtils.isEmpty(_signCode)){
				this.getResponse().getWriter().write(Response.build().setStatus(FrontStatusConstants.NULL_OF_VALIDATE_CARD).toJSON());
				return;
			}
			
			if(signCode.equalsIgnoreCase(_signCode)){
				this.getResponse().getWriter().write(Response.build().setStatus("1").toJSON());
				return;
			}	
			else{
				this.getResponse().getWriter().write(Response.build().setStatus(FrontStatusConstants.INPUT_ERROR_OF_VALIDATE_CARD).toJSON());
				return;
				}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * 产生验证码，保存在redis中
	 */
	@Action("validateCode")
	public void validateCode(){
		String tokenUuid = this.getRequest().getParameter("tokenUuid");
		String uuid = baseCache.get(tokenUuid);
		try {
			if(StringUtils.isEmpty(uuid))
				return;
			String str = ImageUtil.getRundomStr();
			
			baseCache.del(uuid);
			baseCache.set(tokenUuid, str);
			baseCache.expire(tokenUuid, 3*60);
			
			ImageUtil.getImage(str, this.getResponse().getOutputStream());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * 验证用户名是否有效
	 */
	@Action("validateUserName")
	public void validateUserName(){
		String username = this.getRequest().getParameter("username");
		//iUserService.findByUsername(username);
		UserModel user = iUserService.findByUsername(username);
		try {
			if(user == null){
				this.getResponse().getWriter().write(Response.build().setStatus("1").toJSON());
				return;
			}
			else{
				this.getResponse().getWriter().write(Response.build().setStatus(FrontStatusConstants.ALREADY_EXIST_OF_USERNAME).toJSON());
				return;
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Action("validatePhone")
	public void validatePhone(){
		String phone = this.getRequest().getParameter("phone");
		//iUserService.findByUsername(username);
		UserModel user = iUserService.findByPhone(phone);
		try {
			if(user == null){
				this.getResponse().getWriter().write(Response.build().setStatus("1").toJSON());
				return;
			}
			else{
				this.getResponse().getWriter().write(Response.build().setStatus(FrontStatusConstants.MOBILE_ALREADY_REGISTER).toJSON());
				return;
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Action("signup")
	public void regist(){
		boolean flag = iUserService.addUser(user);
		//注册成功，开账户。
		try {
			if(flag){
				accountservice.add(user.getId());
				String token = generateUserToken(user.getUsername());
				Map<String,Integer> map = new HashMap<>();
				map.put("id", user.getId());
				this.getResponse().getWriter().write(Response.build().setStatus("1").setToken(token).setData(map).toJSON());
				return;
			}
			else{
				this.getResponse().getWriter().write(Response.build().setStatus(FrontStatusConstants.BREAK_DOWN).toJSON());
				return;
				
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}

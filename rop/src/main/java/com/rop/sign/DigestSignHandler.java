package com.rop.sign;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Map;

import com.rop.RopException;
import com.rop.config.SystemParameterNames;
import com.rop.security.AppSecretManager;
import com.rop.sign.SignHandler;
import com.rop.utils.RopUtils;

/**
 * 消息摘要数字签名处理实现
 * @author liangruisen
 */
public class DigestSignHandler implements SignHandler {

	public DigestSignHandler() {
		super();
	}

	public DigestSignHandler(String algorithm, AppSecretManager appSecretManager) {
		super();
		this.algorithm = algorithm;
	}

	protected AppSecretManager appSecretManager;
	
	protected String algorithm = "SHA-1";

	/**
	 * 产生签名
	 * @see com.rop.sign.SignHandler#sign(java.util.Map)
	 */
	@Override
	public String sign(Map<String, String> paramMap) {
		return sign(paramMap, null);
	}

	/**
	 * 产生签名
	 * @see com.rop.sign.SignHandler#sign(java.util.Map, java.util.Collection)
	 */
	@Override
	public String sign(Map<String, String> paramValues, Collection<String> ignore) {
		try {
			String appKey = paramValues.get(SystemParameterNames.getAppKey());
			String secret = appSecretManager.getSecret(appKey);
			StringBuilder sb = new StringBuilder();
			sb.append(secret);
			sb.append(RopUtils.getSignContent(paramValues, ignore));
			sb.append(secret);
			byte[] sha1Digest = getDigest(sb.toString());
			return byte2hex(sha1Digest);
		} catch (IOException e) {
			throw new RopException(e);
		}
	}

	/**
	 * 获取消息摘要
	 * @param data
	 * @return
	 * @throws IOException
	 */
	protected byte[] getDigest(String data) throws IOException {
		byte[] bytes = null;
		try {
			MessageDigest md = MessageDigest.getInstance(algorithm);
			bytes = md.digest(data.getBytes());
		} catch (GeneralSecurityException gse) {
			throw new IOException(gse.getMessage());
		}
		return bytes;
	}

	/**
	 * 检查签名是否正确
	 * @see com.rop.sign.SignHandler#signCheck(java.lang.String, java.util.Map, java.util.Collection)
	 */
	@Override
	public boolean signCheck(String sign, Map<String, String> paramMap, Collection<String> ignore) {
		String signValue = sign(paramMap, ignore);
		return signValue.equals(sign);
	}

	/**
	 * 检查签名是否正确
	 * @see com.rop.sign.SignHandler#signCheck(java.lang.String, java.util.Map)
	 */
	@Override
	public boolean signCheck(String sign, Map<String, String> paramMap) {
		return signCheck(sign, paramMap, null);
	}
	
	/**
	 * 二进制转十六进制字符串
	 * @param bytes
	 * @return
	 */
	protected String byte2hex(byte[] bytes) {
		StringBuilder sign = new StringBuilder();
		for (int i = 0; i < bytes.length; i++) {
			String hex = Integer.toHexString(bytes[i] & 0xFF);
			if (hex.length() == 1) {
				sign.append("0");
			}
			sign.append(hex.toUpperCase());
		}
		return sign.toString();
	}

	public void setAlgorithm(String algorithm) {
		this.algorithm = algorithm;
	}
}

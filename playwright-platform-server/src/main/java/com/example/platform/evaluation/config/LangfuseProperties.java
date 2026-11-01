package com.example.platform.evaluation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Langfuse 连接配置（application.yml platform.evaluation.langfuse.*）。 */
@ConfigurationProperties(prefix = "platform.evaluation.langfuse")
public class LangfuseProperties {

    private boolean enabled = true;
    private String host = "http://localhost:3000";
    private String publicKey = "";
    private String secretKey = "";
    /** C12 只读 Key 分离 —— 查询 Trace 用独立 Key，写 Key 只用于写入 */
    private boolean readKeySeparated = false;
    private String readPublicKey = "";
    private String readSecretKey = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public String getHost() { return host; }
    public void setHost(String v) { this.host = v; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String v) { this.publicKey = v; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String v) { this.secretKey = v; }
    public boolean isReadKeySeparated() { return readKeySeparated; }
    public void setReadKeySeparated(boolean v) { this.readKeySeparated = v; }
    public String getReadPublicKey() { return readPublicKey; }
    public void setReadPublicKey(String v) { this.readPublicKey = v; }
    public String getReadSecretKey() { return readSecretKey; }
    public void setReadSecretKey(String v) { this.readSecretKey = v; }

    /** 查询用 Key：若分离则返回独立的 read key，否则回退到 write key。 */
    public String queryPublicKey() {
        return readKeySeparated && readPublicKey != null && !readPublicKey.isBlank()
                ? readPublicKey : publicKey;
    }
    public String querySecretKey() {
        return readKeySeparated && readSecretKey != null && !readSecretKey.isBlank()
                ? readSecretKey : secretKey;
    }
}

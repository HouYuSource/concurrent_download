package cn.shaines.core.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author houyu
 * @createTime 2019/10/5 10:32
 */
public class PropertiesUtil {

    private static final Logger logger = LoggerFactory.getLogger(PropertiesUtil.class);
    private Builder builder;

    protected PropertiesUtil(Builder builder) {
        this.builder = builder;
    }

    public static Builder builder(String path, Charset charset) {
        return new Builder(path, charset);
    }

    public static Builder builder(String path) {
        return builder(path, null);
    }

    public String getProperty(String key) {
        return this.builder.properties.getProperty(key);
    }

    public <T> T getPropertyCastType(String key, Class<T> targetType) {
        Object property = this.builder.properties.get(key);
        if(property == null) {
            return null;
        }
        try {
            return targetType.getDeclaredConstructor(String.class).newInstance(String.valueOf(property));
        } catch(NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            logger.warn("get property not null but cast type has exception ", e);
        }
        return null;
    }

    public <T> T getPropertyOrDefault(String key, T nullDefault) {
        Object property = this.builder.properties.get(key);
        if(property == null) {
            return nullDefault;
        }
        return (T) this.getPropertyCastType(key, nullDefault.getClass());
    }

    public static class Builder {
        private String path;
        private Charset charset;
        private Properties properties;
        private boolean ifNeedDiscernCharset = true;
        public Builder(String path, Charset charset) {
            this.path = path;
            this.charset = charset == null ? Charset.defaultCharset() : charset;
        }

        public PropertiesUtil build() {
            properties = new Properties();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(this.path)), this.charset))) {
                for(String line; (line = reader.readLine()) != null; ) {
                    if(line.startsWith("#")) {
                        continue;
                    }
                    String[] keyOfVal = null;
                    if(line.contains("=")) {
                        // 优先使用=进行切分
                        keyOfVal = line.split("=", 2);
                    } else if(line.contains(":")) {
                        // 其次使用:进行切分
                        keyOfVal = line.split(":", 2);
                    }
                    if(keyOfVal != null && keyOfVal.length == 2) {
                        if(ifNeedDiscernCharset && "properties.charset".equals(keyOfVal[0]) && Charset.isSupported(keyOfVal[1])) {
                            // 使用文件配置编码进行解析文件
                            this.charset = Charset.forName(keyOfVal[1]);
                            this.ifNeedDiscernCharset = false;
                            return this.build();
                        }
                        //
                        properties.put(keyOfVal[0], keyOfVal[1]);
                    }
                }
            } catch(IOException e) {
                logger.warn("custom parse properties file has exception ", e);
            }
            return new PropertiesUtil(this);
        }
    }

    public static void main(String[] args) {
        PropertiesUtil propertiesUtil = PropertiesUtil.builder(System.getProperty("user.dir") + "/conf/img.baidu.properties").build();
        String keyword = propertiesUtil.getProperty("keyword");
        System.out.println("keyword = " + keyword);
        Integer totalCount = propertiesUtil.getPropertyCastType("totalCount2", Integer.class);
        System.out.println("totalCount = " + totalCount);
        totalCount = propertiesUtil.getPropertyOrDefault("totalCount2", 30);
        System.out.println("totalCount = " + totalCount);


    }

}

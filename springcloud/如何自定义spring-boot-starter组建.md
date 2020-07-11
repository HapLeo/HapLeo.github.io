## 如何自定义spring-boot-starter组建？

### 说明

spring-boot-starter组建是springboot的核心，提供了**自动装配**的功能。

例如：通常情况下，整合spring和mybatis需要通过xml将mybatis的对象配置到spring应用上下文中，或者手动写JavaConfig类。因此，每次整合都需要写大量的xml文件或者JavaConfig类。而SpringBoot则提供了开箱即用的功能，也就是说，只需要引入maven依赖，再在yml或properties文件中配置上参数，即可通过@Autowire将需要的对象注入到应用程序中。



## SpringBoot自动装配原理

springboot为什么可以做到开箱即用？

通过拆解Jar包可以发现，每个spring-boot-starter都有一个`META-INF`文件夹，该文件夹下有一个`spring.factories`文件，里面定义了一些需要自动装配的类。

```properties
# Auto Configure
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
org.mybatis.spring.boot.autoconfigure.MybatisLanguageDriverAutoConfiguration,\
org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration
```

springboot在启动时会读取这个文件的内容，来获取需要自动装配的配置类的全限定名。得到全限定名后，会检查这个类的注解，判断是否符合装配条件。

例如：`mybatis-spring-boot-starter`中的`MybatisAutoConfiguration.class`的部分定义如下：

```java
@org.springframework.context.annotation.Configuration
@ConditionalOnClass({ SqlSessionFactory.class, SqlSessionFactoryBean.class })
@ConditionalOnSingleCandidate(DataSource.class)
@EnableConfigurationProperties(MybatisProperties.class)
@AutoConfigureAfter({ DataSourceAutoConfiguration.class, MybatisLanguageDriverAutoConfiguration.class })
public class MybatisAutoConfiguration implements InitializingBean {

  private final MybatisProperties properties;

  private final Interceptor[] interceptors;

  private final TypeHandler[] typeHandlers;
...
  @Bean
  @ConditionalOnMissingBean
  public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
    SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
    factory.setDataSource(dataSource);
    factory.setVfs(SpringBootVFS.class);
	...
    return factory.getObject();
  }

```

这个类有三个值得注意的地方。

- `@Configuration`注解配合`@Bean`注解来生成Bean：这与JavaConfig方式无异，目的是生成Bean并放入容器。
- `@ConditionalOnClass、@ConditionalOnSingleCandidate`：这类注解都以@Conditional为开头，表示在什么情况下才会执行当前自动配置。例如`@ConditionalOnClass({ SqlSessionFactory.class, SqlSessionFactoryBean.class })`表示只有classpath下存在这两个类时才执行自动配置。
- `@EnableConfigurationProperties(MybatisProperties.class)`: 用来获取yaml或properties配置文件中定义的值。`@EnableConfigurationProperties(MybatisProperties.class)`注解的意思是：**使MybatisProperties.class中的@ConfigurationProperties注解生效**，因为MybatisProperties.class才是保存`yaml/properties`中配置项的类。

MybatisProperties类的部分定义如下：

```java
/**
 * Configuration properties for MyBatis.
 *
 * @author Eddú Meléndez
 * @author Kazuki Shimizu
 */
@ConfigurationProperties(prefix = MybatisProperties.MYBATIS_PREFIX)
public class MybatisProperties {

  public static final String MYBATIS_PREFIX = "mybatis";

  private static final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

  /**
   * Location of MyBatis xml config file.
   */
  private String configLocation;

  /**
   * Locations of MyBatis mapper files.
   */
  private String[] mapperLocations;

  /**
   * Packages to search type aliases. (Package delimiters are ",; \t\n")
   */
  private String typeAliasesPackage;

  /**
   * The super class for filtering type alias. If this not specifies, the MyBatis deal as type alias all classes that
   * searched from typeAliasesPackage.
   */
  private Class<?> typeAliasesSuperType;

```

对应到配置文件中的配置项：

```properties
mybatis.mapper-locations=classpath:/mapper/*.xml
mybatis.type-aliases-package=com.example.demo.model
```

> 注意：`MybatisProperties.class`必须使用`@ConfigurationProperties(prefix = MybatisProperties.MYBATIS_PREFIX)`注解才能够取到`yaml/properties`中的属性值。prefix是指定的前缀。

### 自定义spring-boot-starter需要注意的问题

- **只能使用普通的maven项目构建spring-boot-starter**：springboot项目的打包方式默认打成可执行jar而不是普通的jar包，可执行jar包的目录结构与普通jar包有区别。假如你将可执行jar通过mvn install 安装到本地maven库并在自己的项目pom文件中依赖这个jar，你会发现程序中无法import这个jar的类。

- **假如你出现了maven编译版本错误**：首先，修改IDEA的各个jdk版本配置，包括module版本、`settings->Build,Execution,Deployment->Java Compiler`. 其次，在pom.xml中指定properties属性来指定maven编译版本：

  
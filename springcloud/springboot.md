# SpringBoot自动装配原理

在springboot的启动类中，默认在类上标注了`@SpringBootApplication`这个注解，该注解是一个复合注解,包含了`@SpringBootConfiguration` 、 `@EnableAutoConfiguration` 和 `@ComponentScan` 注解。

- `@SpringBootConfiguration` 注解包含的核心注解就是`@Configuration` ，因此可以将其看做`@Configuration` 注解。

- `@ComponentScan` 注解用于开启组件扫描,对所有标注了`@Component`注解的Bean进行扫描，相当于xml配置中的`<context:component-scan base-package="com.foo.bar"/>`; 值得注意的是，`@Configuration`、`@Controller` 、`@Service` 、`@Repository` 注解都包含了`@Component`注解。

-  `@EnableAutoConfiguration` 注解用于开启自动装配，它是一个典型的 `@Enable`前缀系列注解，这一系列的注解的核心成员注解为`@Import` ，用于将Bean导入到ApplicationContext中。这里的`@EnableAutoConfiguration`注解的功能即：**将自动装配相关的Bean导入到容器中**。

下面我们来详细分析`@EnableAutoConfiguration` 注解是如何工作的。

上述我们提到，`@EnableAutoConfiguration`注解包含了`@Import`注解，那么首先要明白`@Import`注解的作用和使用方法。

首先，我们来看一下@Import注解的文档：

```java 
Indicates one or more component classes to import, typically {@link Configuration @Configuration} classes.
	表示要导入一个或多个组件，通常与@Configuration一起使用。

Provides functionality equivalent to the {@code <import/>} element in Spring XML.
	提供了与xml中的<import/> 标签相同的功能；
    
Allows for importing {@code @Configuration} classes, {@link ImportSelector} and
{@link ImportBeanDefinitionRegistrar} implementations, as well as regular component classes (as of 4.2; analogous to {@link AnnotationConfigApplicationContext#register}).
	允许导入被@Configuration标注的类、ImportSelector和ImportBeanDefinitionRegistrar接口的实现类以及普通的组件类。
    
{@code @Bean} definitions declared in imported {@code @Configuration} classes should be
accessed by using {@link org.springframework.beans.factory.annotation.Autowired @Autowired} injection. Either the bean itself can be autowired, or the configuration class instance declaring the bean can be autowired. The latter approach allows for explicit, IDE-friendly navigation between {@code @Configuration} class methods.
	在被导入的@Configuration标注的类中，通过@Bean注解声明的Bean可以通过@Autowired注解注入。不仅Bean本身可以被注入，被@Configuration注解标注的实例本身也可以被注入。后一种方法允许精确的、IDE有好的提示。
 
<p>May be declared at the class level or as a meta-annotation.
    该注解可以用在类级别或者作为元注解。
 
<p>If XML or other non-{@code @Configuration} bean definition resources need to be
imported, use the {@link ImportResource @ImportResource} annotation instead.
    如果XML或者其他的非@Configuration标注的资源需要被导入，则需要使用@ImportResource注解替代。
```



通过源码中的说明可知，@Import注解有四种方式来导入Bean到容器中。下面通过代码来展示这四种方式。

首先定义一个POJO类，作为示例：

```java
@Data
public class User {

    private String username;

    private String account;
}
```

`@Import` 注解将User类导入到容器中的方式如下：

- 直接导入这个类

  ```java
  @Configuration
  @Import(User.class)
  public class BeanImportConfig {
  }
  ```

- 导入提供该Bean的被@Configuration注解的类

  ```java
  @Configuration
  @Import(UserConfig.class)
  public class BeanImportConfig {
  }
  ```

  ```java
  @Configuration
  public class UserConfig {
  
      @Bean
      public User getUser(){
          return new User();
      }
  }
  ```

- 通过实现ImportSelector接口并实现selectImports()方法来导入，该方法适合批量导入多个类。

  ```java
  @Configuration
  @Import(UserImportSelector.class) // 这里指定导入选择器即可批量导入多个类
  public class BeanImportConfig {
  }
  ```

  ```java
  // 实现一个导入选择器，用于批量的指定想要导入的类，selectImports方法指定想要导入的类的全限定名，该方法会在spring执行refresh时被调用
  public class UserImportSelector implements ImportSelector {
  
      @Override
      public String[] selectImports(AnnotationMetadata importingClassMetadata) {
          return new String[]{User.class.getName()};
      }
  
      @Override
      public Predicate<String> getExclusionFilter() {
          return null;
      }
  }
  ```



- 通过实现ImportBeanDefinitionRegistrar接口并导入该实现类，该方法适合需要对容器中的类定义进行更多操作的场景，比如新增、修改、删除、查询类定义

  ```java
  @Configuration
  @Import(UserImportBeanDefinitionRegistrar.class) // 在Configuration中导入一个类定义注册器的实现类，对类定义进行操作
  public class BeanImportConfig {
  }
  ```

  ```java
  // 类定义注册器的自定义实现，通过registerBeanDefinitions方法对类定义进行操作
  public class UserImportBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar {
  
      @Override
      public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
          registry.registerBeanDefinition("user",new RootBeanDefinition(User.class));
          registry.removeBeanDefinition(User.class.getName());
      }
  }
  ```

  
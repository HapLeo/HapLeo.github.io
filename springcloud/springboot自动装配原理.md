# SpringBoot自动装配原理

## 起源

在SpringBoot出现之前，想要将一个Bean注册到Spring容器中，需要在pom.xml文件中手动引入jar包，并在xml中手动定义Bean,这种手动的方式显得非常低效，想要通过这种方式快速搭建一个完备的项目骨架并不容易。SpringBoot的出现就是为了解决快速搭建生产级项目的问题。它提供了**自动装配**的能力，当我们需要某种功能时，只需要将对应的starter包引入pom.xml，SpringBoot就会自动导入该功能的所有jar包并装配相应的Bean。

```xml
<!-- 当我们需要搭建web工程时，只需要引入spring-boot-starter-web即可，无需其他操作 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

那么，SpringBoot是如何做到的呢？

## starter启动器

通过spring官网或IDEA等工具生成一个springboot项目，如果勾选了web模块，你会发现pom.xml中自动引入`spring-boot-starter-web`这个依赖项，这就是web模块的启动器。与此类似的启动器还有很多，比如redis模块的启动器`spring-boot-starter-data-redis`、测试模块启动器`spring-boot-starter-test`等等。

如下是pom.xml中引入的starter：

```java
</properties>
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

 

每个starter都是一个jar包，jar包中包含了一个pom.xml文件，该pom中依赖了这个启动器功能所需的所有依赖。引入了starter之后，项目运行时就可以在classpath下加载到这些jar包中的类。例如，项目中引入了spring-boot-starter-web，而这个jar包中的pom文件中引入了如下的依赖，于是classpath下就有了springMVC相关的类。

```xml
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter</artifactId>
      <version>2.4.3</version>
      <scope>compile</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-json</artifactId>
      <version>2.4.3</version>
      <scope>compile</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-tomcat</artifactId>
      <version>2.4.3</version>
      <scope>compile</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-web</artifactId>
      <version>5.3.4</version>
      <scope>compile</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-webmvc</artifactId>
      <version>5.3.4</version>
      <scope>compile</scope>
    </dependency>
  </dependencies>
```

**所以，我们在需要某种功能的时候，只需要引入对应的starter即可，不再需要逐个手动引入该功能需要的所有依赖项，这简化了我们引入jar包的过程，也避免了版本兼容性问题。**



## 自动装配

jar包引入之后，仍需要将这些依赖项的Bean放到Spring容器中，并做相应的配置，才能被正确的使用。

**SpringBoot通过@EnableAutoConfiguration注解实现了自动配置功能。对于一些常见的功能模块，我们只需要引入对应的starter，SpringBoot就可以自动将其实例化到容器并进行约定的配置。**

在springboot的启动类中，默认在类上标注了`@SpringBootApplication`这个注解，该注解是一个复合注解,包含了`@SpringBootConfiguration` 、 `@EnableAutoConfiguration` 和 `@ComponentScan` 注解。

- `@SpringBootConfiguration` 注解包含的核心元注解就是`@Configuration` ，因此可以将其看做`@Configuration` 注解。

- `@ComponentScan` 注解用于开启组件扫描,对所有标注了`@Component`注解的Bean进行扫描，相当于xml配置中的`<context:component-scan base-package="com.foo.bar"/>`; 值得注意的是，`@Configuration`、`@Controller` 、`@Service` 、`@Repository` 注解都包含了`@Component`注解。

-  `@EnableAutoConfiguration` 注解用于开启自动装配，它是一个典型的 `@Enable`前缀系列注解，这一系列的注解的核心元注解为`@Import` ，用于将Bean导入到ApplicationContext中。这里的`@EnableAutoConfiguration`注解的功能即：**将自动装配相关的Bean导入到容器中**。

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



通过源码中的说明可知，@Import注解有四种方式来导入Bean到容器中。下面通过简单的示例代码来展示这四种方式。

首先定义一个POJO类，我们最终的目的是将User类实例化到Spring容器，示例如下：

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

- 通过实现ImportBeanDefinitionRegistrar接口并导入该实现类，该方法适合需要对容器中的类定义进行更多操作的场景，比如新增、修改、删除、查询容器中的类定义

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

  

  我们通过查看@EnableAutoConfiguration注解的源码可以得知，SpringBoot通过@Import注解引入了一个ImportSelector接口的实现类来实现Bean的自动配置。源码如下：
  
  ```java
  @Import(AutoConfigurationImportSelector.class)
  public @interface EnableAutoConfiguration {
      ...
  }
  ```

由于SpringBoot使用@Import加载ImportSelector接口的实现类的方案来加载依赖项的类实例到容器，**于是我们可以猜测，这个AutoConfigurationImportSelector类的selectImports方法返回了一个由类全限定名组成的数组，这些类就是需要注入到Spring容器的Bean的类名，或者是实例化这些Bean的配置类。**

下面我们通过查看源码来验证这一猜想。

通过前面对@Import用法的学习我们已经知道，ImportSelector接口用于批量导入配置类或者Bean，Spring容器启动时，在refresh()方法中最终会调用到该接口的selectImports()方法，获取该方法所提供的类全限定名组成的数组。因此，我们进入源码查看AutoConfigurationImportSelector这个类的selectImports()方法提供了哪些类的全限定名。

```java
// AutoConfigurationImportSelector.java
// 向Spring提供配置类的全限定名
@Override
public String[] selectImports(AnnotationMetadata annotationMetadata) {
	if (!isEnabled(annotationMetadata)) {
		return NO_IMPORTS;
	}
	AutoConfigurationEntry autoConfigurationEntry = getAutoConfigurationEntry(annotationMetadata);
	return StringUtils.toStringArray(autoConfigurationEntry.getConfigurations());

// 获取候选配置的列表，进行去重过滤等检查后返回
protected AutoConfigurationEntry getAutoConfigurationEntry(AnnotationMetadata annotationMetadata) {
	if (!isEnabled(annotationMetadata)) {
		return EMPTY_ENTRY;
	}
	AnnotationAttributes attributes = getAttributes(annotationMetadata);
	List<String> configurations = getCandidateConfigurations(annotationMetadata, attributes);
	configurations = removeDuplicates(configurations);
	Set<String> exclusions = getExclusions(annotationMetadata, attributes);
	checkExcludedClasses(configurations, exclusions);
	configurations.removeAll(exclusions);
	configurations = getConfigurationClassFilter().filter(configurations);
	fireAutoConfigurationImportEvents(configurations, exclusions);
	return new AutoConfigurationEntry(configurations, exclusions);
}

// 通过SpringFactoriesLoader获取META-INF/spring.factories文件中对应的类路径列表
protected List<String> getCandidateConfigurations(AnnotationMetadata metadata, AnnotationAttributes attributes) {
	List<String> configurations = SpringFactoriesLoader.loadFactoryNames(getSpringFactoriesLoaderFactoryClass(),
			getBeanClassLoader());
	Assert.notEmpty(configurations, "No auto configuration classes found in META-INF/spring.factories. If you "
			+ "are using a custom packaging, make sure that file is correct.");
	return configurations;
}
    
// 返回EnableAutoConfiguration这个类
protected Class<?> getSpringFactoriesLoaderFactoryClass() {
	return EnableAutoConfiguration.class;
}
```

这段代码的执行逻辑如下：

-  @Import注解导入AutoConfigurationImportSelector类；

- Spring容器执行refresh()方法时调用AutoConfigurationImportSelector类的selectImports方法；

- selectImports方法通过调用其他方法最终调用到getCandidateConfigurations方法来获取配置类列表；

- getCandidateConfigurations方法通过SpringFactoriesLoader加载当前类所在jar包下的META-INF/spring.factories文件，并获取文件中`org.springframework.boot.autoconfigure.EnableAutoConfiguration`对应的配置类列表，最终这个列表会通过selectImports方法返回。

  下图是spring.factories文件中找到的配置类列表的部分截图：

  ![image-20210227154046294](../images/image-20210227154046294.png)

从图中我们可以看到大量的配置类的全限定名，Spring会加载这些类并对类中的@Bean进行实例化。

**至此，我们验证了上述猜想，AutoConfigurationImportSelector类中的selectImports方法返回了一个由配置类的全限定名组成的字符串数组，Spring只需要加载这些配置类，并逐个调用类中的@Bean注解的方法，即可实例化所需的Bean实例。**

但是，仍有一个问题没有解决。selectImports方法返回的类名列表是Spring支持自动装配的所有功能列表，我们可能只用到其中的一个或几个而已，没有用到的功能模块自然不需要处理。那么，Spring是如何判断是否应该处理那些配置类呢？

SpringBoot通过每个配置类中标注的@Conditional系列注解来判断是否需要处理相应配置。

例如`org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration`这个配置类，该类是用于配置Web环境的类，部分内容如下：

```java
// WebMvcAutoConfiguration.java
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({ Servlet.class, DispatcherServlet.class, WebMvcConfigurer.class })
@ConditionalOnMissingBean(WebMvcConfigurationSupport.class)
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE + 10)
@AutoConfigureAfter({ DispatcherServletAutoConfiguration.class, TaskExecutionAutoConfiguration.class,
		ValidationAutoConfiguration.class })
public class WebMvcAutoConfiguration {
    
	@Bean
	@ConditionalOnMissingBean(HiddenHttpMethodFilter.class)
	@ConditionalOnProperty(prefix = "spring.mvc.hiddenmethod.filter", name = "enabled", matchIfMissing = false)
	public OrderedHiddenHttpMethodFilter hiddenHttpMethodFilter() {
		return new OrderedHiddenHttpMethodFilter();
	}

}
```

@Configuration注解表明了该类为配置类，类中的@Bean注解的方法会返回Bean实例；

@Conditional系列注解表示在某些条件下执行，例如@ConditionalOnWebApplication表示需要在Web应用中才能使用该配置，@ConditionalOnClass表示在classpath下需要有指定的类才可以使用；@Conditional系列的其他注解类似的见名知意。

**因此，如果我们并没有引入`spring-boot-starter-web`，classpath下就不会存在相应的类，也就无法满足@Conditional系列注解中的条件，此时SpringBoot将不会处理对应的配置。**

由此，Spring自动配置的原理就彻底明白了。通过加载spring-boot-autoconfigure.jar中的预先写好的JavaConfig来完成约定的配置，执行配置时会通过@Conditional系列注解先判断classpath下是否有相应的类，也就是说pom文件中是否引入了相应的starter或者jar包，如果并没有引入该类，则不会执行相关的配置方法。

## 总结

 SpringBoot为我们提供了快速搭建项目的能力，这种能力来源于starter对功能模块的依赖项的封装和约定的配置。当SpringBoot的主类启动时，本质上是通过@Import注解将这些配置类加载并执行其方法，以达到自动配置的目的，@Conditional注解则声明这些配置类和方法的执行条件，达到按需配置的效果。 


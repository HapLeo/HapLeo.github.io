## 如何自定义spring-boot-starter组建？

### 说明

spring-boot-starter组建是springboot的核心，提供了**自动装配**的功能。

例如：通常情况下，整合spring和mybatis需要通过xml将mybatis的对象配置到spring应用上下文中，或者手动写JavaConfig类。因此，每次整合都需要写大量的xml文件或者JavaConfig类。而SpringBoot则提供了开箱即用的功能，也就是说，只需要引入maven依赖，再在yml或properties文件中配置上参数，即可通过@Autowire将需要的对象注入到应用程序中。



## SpringBoot自动装配原理

springboot为什么可以做到开箱即用？

通过拆解Jar包可以发现，每个spring-boot-starter都有一个`META-INF`文件夹，该文件夹下有一个`spring.factories`文件，里面定义了一些需要自动装配的类。




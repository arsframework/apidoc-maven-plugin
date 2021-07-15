# apidoc-maven-plugin
该插件用于自动扫描Java接口注释并生成[apidoc](https://apidocjs.com)接口文档。

## 1 环境依赖
- Java JDK1.8+
- Node node@14.16.0
- Apidoc apidoc@0.24.0

注意：以上apidoc版本仅供参考，在某些高版本环境下可能会出现对象参数字段乱序问题。

## 2 部署配置
在Maven配置中添加如下依赖：
```
<plugin>
    <groupId>com.arsframework</groupId>
    <artifactId>apidoc-maven-plugin</artifactId>
    <version>1.3.8</version>
</plugin>
```

## 3 功能描述
插件将自动扫描所有接口的文档注释，并将接口注释按照apidoc的结构保存到指定文件中，默认存储到当前项目根目录下的${projectName}.apidoc文件中。插件通过解析第三方注解的方式加载接口参数的验证信息，比如参数长度、是否必须、参数格式等。另外还可以通过参数配置控制文档参数显示与否，比如接口作者、日期等。

接口文档构建完成后可以通过命令```apidoc -f projectName.apidoc -o ./apidoc```生成Html文档，详情参考[apidoc官方文档](https://apidocjs.com)。也可以通过```apidoc-swagger```工具生成```swagger.json```，详情参考[apidoc-swagger官方文档](https://www.npmjs.com/package/apidoc-swagger)。

### 3.1 插件参数
#### 3.1.1 ```displayDate```
是否显示接口日期，默认为```false```。

如果参数值为```true```，插件将按照接口方法、接口类的顺序依次解析```@date```注释，直到获取到有效的参数值，没有则不显示。
#### 3.1.2 ```displayAuthor```
是否显示接口作者，默认为```false```。

如果参数值为```true```，插件将按照接口方法、接口类的顺序依次解析```@author```注释，直到获取到有效的参数值，没有则不显示。

#### 3.1.3 ```enableSampleRequest```
是否启用接口请求，默认为```false```。

如果参数值为```true```，接口文档将展示接口请求表单，否则在将接口文档中插入```@apiSampleRequest off```标记来关闭该功能，该参数需要与```apidoc.json```里面的```sampleUrl```参数配合使用。

#### 3.1.4 ```includeGroupIdentities```
接口文档所包含的Maven```groupId```列表，多个值之间使用","号隔开，默认包含当前项目的Group Identity。

插件将根据该配置下载依赖包源码，并从这些源码中去解析接口文档信息，其中接口参数的解析与否也与此参数有关（详情请查看接口解析说明）。

#### 3.1.5 ```output```
接口文档输出文件，默认为```${project.basedir}/${project.name}.apidoc```。

#### 3.1.6 ```enableResponseExample```
是否启用响应参数示例，默认为```true```。

#### 3.1.7 ```enableSnakeUnderlineConversion```
是否启动将参数格式由驼峰转为下划线，默认```false```。如果参数已使用```com.arsframework.spring.web.utils.param.Rename```、```com.fasterxml.jackson.annotation.JsonProperty```、```com.fasterxml.jackson.databind.annotation.JsonNaming```注解，则忽略该参数。

#### 3.1.8 ```analyserFactoryClass```
设置自定义接口解析对象工厂类，通过该参数可覆盖系统默认处理逻辑。

接口解析对象工厂接口：```com.arsframework.apidoc.core.MethodAnalyser.Factory```

#### 3.1.9 ```excludeClasses```
可以通过该参数排除不需要解析的接口（包名+类名），多个接口使用","号隔开。

#### 3.1.10 ```includeHeaders```
通过该参数可添加自定义请求头名称参数。

### 3.2 接口解析
插件根据```includeGroupIdentities```参数的值将源码下载并解压到指定目录中（默认```${project.build.directory}/sources```），然后根据源码加载对应的Class对象并通过Java反射机制查找符合条件的接口方法，然后通过解析对应的源码文档生成```com.arsframework.plugin.apidoc.Api```对象，最后统一转换成apidoc工具能够识别的接口文档文件。

#### 3.2.1 接口过滤
插件将解析使用了Spring接口注解的类和方法：```org.springframework.stereotype.Controller```、```org.springframework.web.bind.annotation.RestController```、```org.springframework.web.bind.annotation.PostMapping```、```org.springframework.web.bind.annotation.GetMapping```、```org.springframework.web.bind.annotation.PutMapping```、```org.springframework.web.bind.annotation.DeleteMapping```、```org.springframework.web.bind.annotation.PatchMapping```、```org.springframework.web.bind.annotation.RequestMapping```；插件通过拼接类和方法的接口地址配置来生成最终的接口地址；插件通过解析接口方法或接口参数的```java.lang.Deprecated```注解来生成接口或参数是否过时的标记。

#### 3.2.2 请求参数过滤
接口请求参数必须符合下列条件之一：
1、未使用```com.fasterxml.jackson.annotation.JsonIgnore```和```org.springframework.web.bind.annotation.SessionAttribute```注解的字段或参数；
2、参数类型为基本数据类型（含包装类）或者元数据类型或者参数类型包名以```includeGroupIdentities```参数中的任意一个开头；

元类型包括：```java.util.Map```、```java.lang.Enum```、```java.lang.Number```、```java.util.Locale```、```java.util.TimeZone```、```java.lang.CharSequence```、```java.util.Date```、```java.time.LocalDate```、```java.time.LocalDateTime```、```java.io.File```、```java.io.Reader```、```java.io.OutputStream```、```java.io.Writer```、```org.springframework.web.multipart.MultipartFile```、```java.io.InputStream```、```org.springframework.core.io.InputStreamSource```。

#### 3.2.3 请求参数描述
插件通过对一些第三方框架解析来生成参数描述信息，具体支持的参数注解包括：```javax.validation.constraints.Max```、```javax.validation.constraints.Min```、```javax.validation.constraints.Size```、```javax.validation.constraints.Pattern```、```javax.validation.constraints.NotNull```、```javax.validation.constraints.NotEmpty```、```javax.validation.constraints.NotBlank```、```javax.validation.constraints.DecimalMax```、```javax.validation.constraints.DecimalMin```、```com.fasterxml.jackson.annotation.JsonFormat```、```com.fasterxml.jackson.annotation.JsonProperty```、```com.fasterxml.jackson.databind.annotation.JsonNaming```、```com.fasterxml.jackson.annotation.JsonSubTypes```、```com.fasterxml.jackson.annotation.JsonTypeInfo```、```org.springframework.format.annotation.DateTimeFormat```、```org.springframework.web.bind.annotation.RequestParam```、```lombok.Builder.Default```、```com.arsframework.spring.web.utils.param.Rename```。

- 涉及参数命名更新的解析顺序：

1、参数是否已使用```com.arsframework.spring.web.utils.param.Rename```注解；

2、参数是否已使用```com.fasterxml.jackson.annotation.JsonProperty```注解；

3、参数是否已使用```com.fasterxml.jackson.databind.annotation.JsonNaming```注解；

4、插件是否启用驼峰转下划线功能，即```enableSnakeUnderlineConversion```参数设置为```true```；

针对枚举类型参数将解析枚举所有选项值，并将其作为参数描述的以部分；针对递归参数将向下获取一级。

#### 3.2.4 响应参数示例
插件将自动获取方法或字段注释中的```@example```参数值，如果没有则根据返回参数生成响应示例（JSON格式），具体参数类型生成示例的规则如下：

- 整形数字

所有整形数字都将生成```1```，整形数字类型（含包装类型）包括：```byte```、```short```、```int```、```long```、```java.math.BigInteger```。

- 浮点数字

所有浮点数字都将生成```1.0```，浮点数字类型（含包装类型）包括：```float```、```double```、```java.math.BigDecimal```。

- 布尔类型

所有布尔类型都将生成```true```。

- 日期类型

如果日期参数需要格式化处理，则使用日期格式化信息对当前日期进行格式化；否则获取当前日期毫秒数。日期类型包括：```java.util.Date```、```java.time.LocalDate```、```java.time.LocalDateTime```。

- 字符串类型

```java.lang.CharSequence```、```java.util.Locale```、```java.util.TimeZone```、```java.lang.Enum```类型都统一视为字符串类型，其中```java.util.Locale```、```java.util.TimeZone```类型将调用获取默认配置方法；```java.lang.CharSequence```将生成```""```；```java.lang.Enum```将获取第一个枚举值。

- 字节流类型

所有字节流类型都将生成```[0b00000001]```，字节流类型包括：```java.io.File```、```java.io.Reader```、```java.io.OutputStream```、```java.io.Writer```、```org.springframework.web.multipart.MultipartFile```、```java.io.InputStream```、```org.springframework.core.io.InputStreamSource```。

## 4 版本更新日志
### v1.1.0
1. 新增对方法、字段注释中的```@example```参数解析，在获取响应参数示例时优先获取该参数配置；
2. 新增是否启用响应示例参数```enableResponseExample```，默认为```true```；
3. 修复响应参数示例生成逻辑问题；

### v1.1.1
1. 修复请求参数过滤时空指针异常问题；
2. 修复接口分组混乱问题；
3. 优化接口文件写入逻辑；

### v1.1.2
1. 将接口请求方式改为小写；
2. 优化接口文件写入逻辑，如果项目没有接口则无需生成接口文件；

### v1.1.3
1. 修复泛型类型解析失败问题；

### v1.2.0
1. 新增插件参数```enableSnakeUnderlineConversion```，用于控制是否将参数格式由驼峰转为下划线；
2. 新增针对```com.fasterxml.jackson.databind.annotation.JsonNaming```、```com.arsframework.spring.web.utils.param.Rename```注解的参数解析支持；

### v1.2.1
1. 调整```@author```注释的解析，支持多个作者的提取；
2. 在处理```@version```注解时，同时兼容```@since```注解的解析；
3. 将参数```enableSampleRequest```的默认值调整为```true```;
4. 优化代码逻辑；

### v1.3.0
1. 将文档处理核心逻辑拆分成单独的JAR包，参考：https://github.com/arsframework/apidoc-core;
2. 新增插件参数```analyserFactoryClass```，可自定义接口处理逻辑；

### v1.3.1
1. 修复当Java文件内容为空时加载类对象出错问题;

### v1.3.2
1. 更新类加载错误日志;

### v1.3.3
1. 修复因出现ClassNoDefException异常而导致运行终止的问题;

### v1.3.4
1. 新增excludeClasses配置参数;

### v1.3.5
1. 新增includeHeaders配置参数;

### v1.3.6
1. 更新```apidoc-core```依赖版本;

### v1.3.7
1. 更新```apidoc-core```依赖版本，修复获取参数默认值异常问题;

### v1.3.8
1. 更新```apidoc-core```依赖版本;
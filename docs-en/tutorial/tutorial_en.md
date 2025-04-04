  # Development Tutorial

## Nop platform source code:

* gitee: [https://gitee.com/canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
* github: [https://github.com/entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)

The Nop platform is a concrete implementation of reversible computation theory. To illustrate the related concepts of reversible computation theory, it incorporates an embedded low-code development process for the backoffice management system. This allows for rapid development of the backoffice management system using low-code techniques and provides automatic product customization capabilities through the platform's built-in mechanisms without requiring special design. The following example introduces the built-in low-code development process of the Nop platform by taking the nop-app-mall project as an instance.

> Nop-app-mall is a sample application of a simple electronic mall. The project source code is available at [nop-app-mall](https://gitee.com/canonical-entropy/nop-app-mall)

## 1. Design Excel Data Model

First, we need to design an Excel data model where database tables, fields, and their association information are defined.

![](excel-model.png)

In the Excel model, the following information can be specified:

1. **Tag**: Used to add additional annotations for tables, fields, etc. For example:
   - `seq` indicates that the field value is automatically generated by SequenceGenerator.
   - `var` indicates that the field value will be initialized as a random value and should be marked as a dynamic variable in automated unit tests.

2. **Display**: Determines whether the field is displayed and whether it can be updated. The information provided here is utilized by the frontend to automatically generate list and form pages.

3. Domain: For a field type with special business semantics, you can specify a specific data domain for it, and the system will process it uniformly after identifying the data domain. For example, if the domain is set to `createTime`, this indicates that the field is a creation time field, which is automatically initialized to the current time when a new entity is created.

4. Dictionary: The data dictionary is used to restrict the value of a field to a limited range of options. A dictionary can be implemented as an enum class in Java, such as `io.nop.xlang.xdef.XDefOverride`. It can also be defined in a YAML file, for example, `mall/aftersale-status` corresponds to the file `/nop/dict/mall/aftersale-status.dict.yaml`.

5. Association: The association relationship between tables can be configured through the Association List. `[Attribute Name]` refers to the attribute name of the corresponding parent entity in the child table entity, and `[Associated Attribute Name]` refers to the attribute name of the corresponding child table entity set in the parent entity. For example, the `[Attribute Name]` corresponding to the `parentId` associated field in the department table is `parent`, and the `[Associated Attribute Name]` is `children`. If you do not need to access the entity collection through the ORM engine, you do not need to configure the associated attribute name.

The dictionary table can be directly defined in the Excel model, and the `dict.yaml` definition dictionary file will be automatically generated according to the model during code generation.

![dict-model.png](dict-model.png)

More detailed configuration information can be found in the documentation
[excel-model.md](../dev-guide/model/excel-model.md)

### Reverse engineering

In addition to manually writing the database model, you can also connect to an existing database and use the `nop-cli` command-line tool to reverse analyze the database structure and generate the Excel model.

```shell
java -Dfile.encoding=UTF8 -jar nop-cli.jar reverse-db litemall -c=com.mysql.cj.jdbc.Driver --username=litemall --password=litemall123456 --jdbcUrl="jdbc:mysql://127.0.0.1:3306/litemall?useUnicode=true&characterEncoding=utf-8&useSSL=true&serverTimezone=UTC"
```

The reverse-db command for nop-cli requires specifying the database schema name, such as litemall, and then passing JDBC connection string information through options like jdbcUrl.

### Import a PowerDesigner or PDManer Model

The gen-orm-excel command in the nop-cli tool allows you to generate an Excel data model from the PDM physical model of a Power Designer design tool.

```shell
java -Dfile.encoding=UTF8 -jar nop-cli.jar gen-orm-excel model/test.pdm
```

An open-source alternative to the fee-based Power Designer is [ PDManager Metadata Modeling Tool ](https://gitee.com/robergroup/pdmaner).

The nop-cli tool can also be used to generate Excel data models from PDManer model files.

```shell
java -Dfile.encoding=UTF8 -jar nop-cli.jar gen-orm-excel model/test.pdma.json
```

According to the theory of reversible computation, Pdm model, PDManer model, and Excel model are merely visual representations of the ORM domain model. \*\* These representations contain the same information and can, in principle, be transformed into each other \*\*. With the metaprogramming capabilities of the Nop platform, we can automatically generate orm model files based on the Pdm model during compilation, allowing us to directly use PowerDesigner or PDManer as the visual design tool for the ORM model within the Nop platform.

```xml
<!-- app.orm.xml -->
<orm x:schema="/nop/schema/orm/orm.xdef"
     x:extends="base.orm.xml" x:dump="true"
     xmlns:x="/nop/schema/xdsl.xdef" xmlns:xpl="/nop/schema/xpl.xdef">
    <x:gen-extends>
        <pdman:GenOrm src="test.pdma.json" xpl:lib="/nop/orm/xlib/pdman.xlib"
                      versionCol="REVISION"
                      createrCol="CREATED_BY" createTimeCol="CREATED_TIME"
                      updaterCol="UPDATED_BY" updateTimeCol="UPDATED_TIME"
                      tenantCol="TENANT_ID"
        />
    </x:gen-extends>
</orm>
```

`x:gen-extends` is a compile-time mechanism that dynamically generates an inheritable base model from the XPL template defined in `x:gen-extends` during the loading of the `app.orm.xml` model file. It then applies delta merging using the x-extends merging algorithm on the inherited content.

> `x:gen-extends` is a built-in syntax of the XLang language, which is detailed in [xdsl.md](../dev-guide/xlang/xdsl.md)

## 2. Generate Initial Project Code

If you have already obtained an Excel data model, you can use the `gen` command of the `nop-cli` command-line tool to generate the initial project code.

```shell
java -jar nop-cli.jar gen -t=/nop/templates/orm model/app-mall.orm.xlsx
```

The generated structure is as follows:

```
├─app-mall-api       Interface definition and message definition exposed by app-mall-api
├─app-mall-codegen   Code generation auxiliary project
├─app-mall-dao       Database entity definition and ORM model
├─app-mall-service   GraphQL service implementation
├─app-mall-web       AMIS page file and View model definition
├─app-mall-app       Test used packaging project
├─deploy             Database creation statements generated according to Excel model
```

The Nop platform integrates code generation with Maven. You only need to add the following configuration in the POM file:

```xml
<pom>
    <parent>
        <artifactId>nop-entropy</artifactId>
        <groupId>io.github.entropy-cloud</groupId>
        <version>2.0.0-SNAPSHOT</version>
    </parent>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</pom>
```

> If you do not inherit from the POM file of the io.nop:nop-entropy module, you need to add more detailed configuration for the exec-maven-plugin plug-in.

When the Maven packaging function is used, the xgen code under the precompile and postcompile directories of the project will be automatically executed. The precompile phase is executed before the compile phase, allowing the execution environment to access all dependent libraries but not the class directory of the current project. The postcompile phase, on the other hand, is executed after the compile phase and has access to the compiled class and resource files. For example, the contents of the precompile/gen-orm.xgen file for the app-mall-codegen module are

```xml
<c:script>
// 根据ORM模型生成dao/entity/xbiz
codeGenerator.withTargetDir("../").renderModel('../../model/app-mall.orm.xlsx','/nop/templates/orm', '/',$scope);
</c:script>
```

The above instruction is equivalent to manually executing the nop-cli gen command. Once the initial project is generated, the code for the current project can then be updated against the Excel data model through Maven packaging, eliminating the need to use the nop-cli tool \*\*. The Nop platform uses an incremental code generation design, and regeneration does not override manually adjusted business code. See the article for the design principles.

Data-Driven Code Generator (https://zhuanlan.zhihu.com/p/540022264)

To facilitate debugging, the initially generated code contains two test classes: AppMallCodeGen.Java and AppMallWebCodeGen.Java, which can be directly started in IDEA to execute code generation.

The code generated from the ORM model includes a complete set of code from the frontend to the backend, which can be compiled and run directly using the mvn install command. Without additional configuration, the test application can be launched with the following command:

```shell
mvn clean install -DskipTests -Dquarkus.package.type=uber-jar

java -Dfile.encoding=UTF8 -Dquarkus.profile=dev -jar app-mall-app/target/app-mall-app-1.0-SNAPSHOT-runner.jar
```

app-mall-app uses the built-in H2 memory database to start, and will automatically create database tables according to the ORM model when starting. The default user is "nop", with a password of "123".

## 3. Configure Menus and Authorization Rules

The automatically generated code includes the permission definition file: `app-mall-web/src/resources/_vfs/app/mall/auth/_app-mall.action-auth.xml`, in which the default menu item is defined for each backend entity, corresponding to the standard CRUD page.

In the app-mall.action-auth.xml file, you can manually add new menu items or mark them for deletion.

```xml
 <resource id="goods-manage" displayName="Goods Management" icon="ion:grid-outline" resourceType="TOPM"
           routePath="/goods-manage" component="layouts/default/index">
     <children>
         <resource id="mall-goods-list" displayName="Goods List"
                   icon="ant-design:appstore-twotone" component="AMIS"
                   resourceType="SUBM" url="/app/mall/pages/LitemallGoods/main.page.yaml"/>
         <resource id="mall-goods-create" displayName="Goods Shelf"
                   icon="ant-design:appstore-twotone" component="AMIS"
                   resourceType="SUBM" url="/app/mall/pages/LitemallGoods/add.page.yaml"/>
         <resource id="mall-goods-comment" displayName="Goods Comments"
                   icon="ant-design:appstore-twotone" component="AMIS"
                   resourceType="SUBM" url="/app/mall/pages/LitemallComment/main.page.yaml"/>
     </children>
 </resource>

The menu structure is designed by the jeecgboot project. The top-level menu is configured with resourceType = TOPM, component = layouts/default/index, and the specific page is configured with resourceType = SUBM, component = AMIS. The URL is configured as the virtual path of the page file.

> At present, the underlying engine has supported the configuration of operation permission and data permission, but the specific permission verification interface has not been fully implemented.

## 4. Improve back-end services

The Nop platform will automatically generate Meta metadata description files and corresponding GraphQL service objects \*\* A full-featured GraphQL backend service with simple configuration \*\* according to the Excel data model. Comparing the Litemall GoodsBizModel.java in the app-mall project with the AdminGoodsService.java implementation class in the original litemall project, it is clear that the Nop platform can avoid a lot of repetitive code. Generally, it is only necessary to express the difference logic that deviates from the standard CRUD logic.

### 4.1 Add Entity Function

Fixed logic that depends only on entity fields can be directly implemented as methods of entity objects. For example, the retail Price field on the item table corresponds to the lowest price for the relevant product.

```java
class LitemallGoods extends _LitemallGoods{
    /**
     * retailPrice记录当前商品的最低价
     */
    public void syncRetailPrice() {
        LitemallGoodsProduct minProduct = Underscore.min(getProducts(), LitemallGoodsProduct::getPrice);
        BigDecimal retailPrice = minProduct == null ? minProduct.getPrice() : new BigDecimal(Integer.MAX_VALUE);
        setRetailPrice(retailPrice);
    }
}
```

With the help of NopOrm data access engine, it is very convenient to directly access the collection of associated objects.

> All associated objects and collections of associated objects are lazy-loaded. The BatchLoadQueue mechanism can be utilized to pre-load in advance, thus avoiding the N + 1 problem common in Hibernate. See for [orm.md](../dev-guide/orm/orm.md) for details.

### 4.2. Add supplementary logic

The Nop platform can automatically realize all logics of CRUD by using the description information defined in the XMeta file, for example:

1. Automatically verify the validity of the parameter according to the metadata configuration (whether it is not empty, whether it is within the dictionary table range, whether it meets the format requirements, etc.)

2. Realize complex query conditions, as well as paging and sorting

3. Submit the master-sub table information at one time, and automatically identify the changes to the sub-table data

4. The query condition of deleted = false is automatically added when logical deletion is enabled

5. Verify that the unique key constraint is not broken, for example, goods names are not allowed to be duplicated

6. Automatically record the creator, creation time, modifier, modification time, etc.

7. When the current entity is deleted, all associated objects marked as cascade-delete are automatically deleted

Functions such as defaultPrepareSave can be overwritten in the derived class of CrudBizModel when we need to enhance the standard CRUD logic. For example, when saving the product information, the redundant field retailPrice on the product is automatically synchronized.

```java
@BizModel("LitemallGoods")
public class LitemallGoodsBizModel extends CrudBizModel<LitemallGoods> {
    public LitemallGoodsBizModel() {
        setEntityName(LitemallGoods.class.getName());
    }

    @Override
    protected void defaultPrepareSave(EntityData<LitemallGoods> entityData, IServiceContext context) {
        entityData.getEntity().syncRetailPrice();
    }

    @Override
    protected void defaultPrepareQuery(QueryBean query, IServiceContext context) {
        TreeBean filter = query.getFilter();
        if (filter != null) {
            TreeBean keywordsFilter = filter.childWithAttr("name", LitemallGoods.PROP_NAME_keywords);
            if (keywordsFilter != null) {
                Object value = keywordsFilter.getAttr("value");
                TreeBean orCond = or(contains(LitemallGoods.PROP_NAME_name, value), contains(LitemallGoods.PROP_NAME_keywords, value));
                filter.replaceChild(keywordsFilter, orCond);
            }
        }
    }
}
```

The above example code also overwrites the defaultPrepareQuery function. In this function, we transform the complex query conditions submitted by the frontend. For example, the keywords query field submitted by the frontend can be transformed into a fuzzy query for the keywords and name fields in the database table at the same time.

### 4.3. Increase database access

If a mapper tag is added to a database table in the Excel model, a sql-lib.xml file and a Mapper interface will be automatically generated, and we can use the SqlLibManager mechanism, which is more convenient and powerful than MyBatis, to implement database access. The sql-lib file supports the use of EQL object query syntax or native SQL syntax. The EQL syntax can be adapted to most relational databases through the Dialect model, so it is generally recommended to use the EQL syntax as much as possible.

```xml
<!-- LitemallGoods.sql-lib.xml -->
<sql-lib x:schema="/nop/schema/orm/sql-lib.xdef" xmlns:x="/nop/schema/xdsl.xdef">

    <sqls>
        <eql name="syncCartProduct" sqlMethod="execute">
            <arg name="product"/>

            <source>
                update LitemallCart o
                set o.price = ${product.price},
                  o.goodsName = ${product.goods.name},
                  o.picUrl = ${product.url},
                  o.goodsSn = ${product.goods.goodsSn}
                where o.productId = ${product.id}
            </source>
        </eql>
    </sqls>
</sql-lib>
```

Similar to MyBatis, EL expressions are automatically replaced with SQL parameters in the source section of sql-lib, and because the EQL syntax can deduce the field types from the ORM model, there is no need to specify JDBC parameter types for each expression as MyBatis does. For detailed documentation, see [sql-lib.md](../dev-guide/orm/sql-lib.md)

Add the corresponding Java method in the Mapper interface:

```java
@SqlLibMapper("/app/mall/sql/LitemallGoods.sql-lib.xml")
public interface LitemallGoodsMapper {

    void syncCartProduct(@Name("product") LitemallGoodsProduct product);
}
```

The IEntityDao interface in the NOP platform is similar to JpaRespository in Spring, but it provides more functionality. For detailed documentation, see [dao.md](../dev-guide/orm/dao.md)

### 4.4 Add Query/Mutation/Loader of GraphQL

Normal Java methods can be converted into GraphQL service methods by adding `@BizQuery/@BizMutation/@BizLoader` annotations to them.

```java
@BizModel("NopAuthRole")
public class NopAuthRoleBizModel extends CrudBizModel<NopAuthRole> {
    public NopAuthRoleBizModel() {
        setEntityName(NopAuthRole.class.getName());
    }

    @BizLoader
    @GraphQLReturn(bizObjName = "NopAuthUser")
    public List<NopAuthUser> roleUsers(@ContextSource NopAuthRole role) {
        return role.getUserMappings().stream()
                .map(NopAuthUserRole::getUser)
                .sorted(comparing(NopAuthUser::getUserName))
                .collect(Collectors.toList());
    }

    @BizMutation
    public void removeRoleUsers(
            @Name("roleId") String roleId,
            @Name("userIds") Collection<String> userIds) {
        removeRelations(NopAuthUserRole.class,
                "roleId", "userId",
                roleId, userIds);
    }
}
```

`@BizLoader` Indicates that an extended attribute is added to the specified object. For example, there is no roleUsers attribute on the NopAuthRole object, but as long as the attribute definition is added to the xmeta file, and then a BizLoader is defined, all requests returning the Role object can access the attribute. For example:

```graphql
query{
    NopAuthRole__get(id: 'admin'){
       name
       roleUsers
    }

    NopAuthRole__findPage(query:$query){
       name
       roleUsers
    }
}
```

See [ graphql-java.md ](../dev-guide/graphql/graphql-java.md) for more details.

### 4.5 Define the xbiz model

The Nop platform provides built-in support for **No Code Development**. Through the xbiz model, we can add and modify Query/Mutation/DataLoader in the GraphQL model online without modifying the Java source code.

The xbiz model has a built-in finite state automaton model, and some simple state transition logic can be completed through configuration without programming in Java. Such as simple approval status migration.

With XDSL's built-in `x:gen-extends` mechanism, we can introduce workflow support for the xbiz model in one sentence.

```xml
<biz>
   <x:gen-extends>
      <!-- Dynamically generate backend workflow-related service functions for business objects -->
      <biz-gen:GenWorkflowSupport/>
   </x:gen-extends>
</biz>
```

> The integration of the workflow engine is currently in its initial phase.

## 5. Enhance the frontend page

### 5.1 Refine Forms and Tables

The view outline model (view.xml) is a front-end domain-specific language (DSL) that is independent of the specific implementation framework and fully focused on the business domain. It abstracts key elements such as grid, form, layout, page, dialog, and action, allowing common addition, deletion, modification, and query logic to be described through simple configurations. This approach is significantly more compact compared to generic page-oriented description models. For example, when adjusting the new and modified pages of the commodity object, only the following layout description is needed:

```xml
<form id="edit" size="lg">
    <layout>
        ========== intro[Product Introduction] ================
        goodsSn[Product ID] name[Product Name]
        counterPrice[Market Price]
        isNew[Is New Product] isHot[Is Popular Recommendation]
        isOnSale[Is On Sale]
        picUrl[Main Product Image URL]
        gallery[Promotional Images List, using JSON array format]
        unit[Product Unit, e.g., piece, box]
        keywords[Product Keywords, comma-separated]
        categoryId[Product Category ID] brandId[Brand ID]

        ========= specs[Product Specifications] ========
        !specifications

        ========= goodsProducts[Product Inventory] ========
        !products

        ========= attrs[Product Attributes] ========
        !attributes

    </layout>
    <cells>
        <cell id="unit">
            <placeholder>piece/individual/item/package</placeholder>
        </cell>
        <cell id="specifications">
            <!-- Can be used with gen-control to specify the control type for the field -->
            <gen-control>
                <input-table addable="@:true" editable="@:true"
                             removable="@:true" />
                ...
            </gen-control>
            <selection>id, specification, value, picUrl</selection>
        </cell>
        <cell id="products">
            <!-- Can reference an external view model grid to display subtables -->
            <view path="/app/mall/pages/LitemallGoodsProduct/LitemallGoodsProduct.view.xml"
                  grid="ref-edit"/>
        </cell>
    </cells>
</form>

Layout is a specialized layout domain language that separates the layout information from the presentation control information of specific fields. The control used by a specific field is generally determined by the data type or data domain setting, and we only need to supplement the layout information to realize the page display. For a detailed description of the layout language, see [layout.md](../dev-guide/xui/layout.md).

**According to the form and table model, the Nop platform will automatically analyze and obtain the backend field list they need to access, so as to automatically generate the selection part of the graphql request, so as to avoid the inconsistency between the UI interface and the background request data caused by manual writing.**

### 5. Adjust field linkage logic

Field linkage logic can be expressed through data binding property expressions, such as

```
 <cell id="pid">
     <requiredOn>${level == 'L2'}</requiredOn>
     <visibleOn>${level == 'L2'}</visibleOn>
 </cell>
```

### 5.3 Adjust action buttons and navigation logic

Common crud pages, single form pages (simple), and multi-tab pages (tab) can be defined and adjusted in the view outline model, and the defined form and table models can be directly referenced in the page model.

> Additional page models can be supported by customizing the xview.xdef metamodel file

```xml
        <crud name="main" grid="list">
            <listActions>
                <!-- Modify the functionality of the add and new buttons to jump to the add page -->
                <action id="add-button" x:override="merge-replace" actionType="link" url="/mall-goods-create"/>

            </listActions>

            <!-- "bounded-merge" indicates that the merged result will be within the current model's range. In the base model, there are child nodes that do not exist in the current model and will be automatically deleted. The default generated code has already defined row-update-button and row-delete-button, with x:abstract="true" configured for both. Therefore, here you only need to declare the id to enable inheritance of the buttons, reducing redundant code -->
            <rowActions x:override="bounded-merge">
                <!-- Use a drawer instead of a dialog box to display the edit form -->
                <action id="row-update-button" actionType="drawer"/>

                <action id="row-delete-button"/>

            </rowActions>
        </crud>
```

### 5.4 Visual Designer

The actual front-end framework currently used by the Nop platform is [Baidu's AMIS Framework](https://aisuda.bce.baidu.com/amis/zh-CN/docs/index), which uses a YAML-formatted page file. In the browser address bar, we directly enter the path to view the contents of the page file (No need to register the path in the front-end router), for example:

```
http://localhost:8080/index.html#//amis/app/mall/pages/LitemallGoods/main.page.yaml
```

The actual corresponding page is located at `src/main/resources/_vfs/app/mall/pages/LitemallGoods/main.page.yaml`, and its content is:

```yaml
x:gen-extends: |
    <web:GenPage view="LitemallGoods.view.xml" page="main"
         xpl:lib="/nop/web/xlib/web.xlib" />

This file represents the generation of the AMIS description based on the page model defined in the LitemallGoods.view.xml view outline model.

1. If the page we need to implement is special and cannot be effectively described by the view outline model, we can directly write the page.yaml file and skip the configuration of the view outline model. That is to say,\*\* Front-end pages have the full capabilities of the AMIS framework and are not limited by the view outline model \*\*.

2. Even if we write page.yaml files by hand, we can still introduce local form or grid definitions through x:gen-extends to simplify page writing. Nested JSON nodes can also be dynamically generated using x:gen-extends or x:extends.

3. The view model defines the page presentation logic that is independent of the specific implementation technology. In principle, it can be adapted to any front-end framework technology. The Nop platform will consider integrate Alibaba's LowCodeEngine in the future.

4. Based on the automatically generated JSON pages, we manually make delta corrections to the generated code in the page.yaml file (using XDSL's built-in Delta merging technique).

In debug mode, all front-end AMIS pages have two design buttons in the upper right corner.

![ amis-editor ](amis-editor.png)

1. If you manually modify the page.yaml or view.xml model file in the backend, you can click Refresh Page to update the frontend

2. Click the JSON design button to pop up the YAML editor, which allows you to modify the JSON description directly in the frontend and then see the display immediately.

3. Clicking the visual design button will pop up the amis-editor visual designer, allowing developers to adjust the content of the page through the visual designer. \*\* Click Save to reversely calculate the difference between the complete page and the generated View, and then save the difference to the page.yaml files \*\*。

   ![](amis-editor-view.png)

For example, after modifying the title of the \[Product Listing\] page in the visual designer to \[Add Product\] and saving it, the content in the add.page.yaml file is

```yaml
x:gen-extends: |
  <web:GenPage view="LitemallGoods.view.xml" page="add" xpl:lib="/nop/web/xlib/web.xlib" />
title: '@i18n:LitemallGoods.forms.add.$title|Add Product'
```

The saved content has been converted to delta form.

### 5.5 Introduction to Custom Modules

The source code of the front-end framework of the Nop platform is included in the project [nop-chaos](https://gitee.com/canonical-entropy/nop-chaos). Generally, we use the built-in components of the framework to develop applications. At this time, we only need to introduce the pre-compiled nop-web-site module on the Java side, and there is no need to recompile the front-end nop-chaos project.

The front-end framework is mainly developed using vue3.0, ant-design-vue, and the Baidu AMIS framework. We have made some extensions based on the AMIS framework. For details, see the document [amis.md](../dev-guide/xui/amis.md). Nop-chaos has built-in SystemJs module loading functionality, which can dynamically load front-end modules. For example

```json
{
    "xui:import": "demo.lib.js"
    // The demo.xxx can be used to access the contents defined in the demo module.
}
```

## 6. Development and Debugging

The Nop platform systematically uses metaprogramming and DSL domain languages for development, and provides a series of auxiliary development and debugging tools.

1. All compile-time composed models are output to the `_dump` directory, where the source code location for each node and attribute is recorded

2. The nop-idea-plugin module provides an IDEA development plug-in that implements code completion, format verification, and other functions based on the xdef metamodel definition. It also provides breakpoint debugging functionality for the XScript scripting language and the Xpl template language.

# 3. Quarkus Framework

The Quarkus framework includes an integrated GraphQL development tool, graphql-ui, which allows users to view all GraphQL type definitions online in the background and provides code suggestions, auto-completion, and other functions.

For detailed documentation, refer to [debug.md](../dev-guide/debug.md).

## Images

![](xlang-debugger.png)

![](graphql-ui.png)

## 7. Automated Testing

The Nop platform features a built-in model-driven automated testing framework that enables fully automated test data preparation and verification without requiring special programming.

```java
public class TestLitemallGoodsBizModel extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @EnableSnapshot
    @Test
    public void testSave() {
        ContextProvider.getOrCreateContext().setUserId("0");
        ContextProvider.getOrCreateContext().setUserName("test");

        ApiRequest<?> request = input("request.json5", ApiRequest.class);
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(GraphQLOperationType.mutation,
                "LitemallGoods__save", request);
        Object result = FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
        output("response.json5", result);
    }
}
```

NopAutoTest employs a recording and replay mechanism to construct test cases.

1. Inherited from the JunitAutoTestCase base class
2. Write a test method that uses the input function to read input data and the output function to write result data. Input data and output data will be saved to the `cases` directory during initial execution.
3. After successful execution, add the @EnableSnapshot annotation to the test method to enable the use of snapshot data generated by the test.
4. Running the test again after enabling snapshots will initialize an in-memory database with the recorded data and automatically verify that the returned result data matches the recorded data and that modifications to the database are consistent.

For a more detailed explanation, see [autotest.md](../dev-guide/autotest.md).

## 8. Delta Customization

All the XDSL model files are stored in the `src/resources/_vfs` directory and form a virtual file system. This virtual file system supports the concept of Delta Layered Overlay (similar to the overlay-fs layered file system in Docker technology), which has layers `_/_delta/default` by default (more layers can be added through configuration). That is, if there exists both a file `/_vfs/_delta/default/nop/app.orm.xml` and a file `/nop/app.orm.xml`, the version in the delta directory will actually be used. In the delta customization file, you can use `x:extends="raw:/nop/app.orm.xml"` to inherit the specified base model or use `x:extends="super"` to inherit the base model of the previous level.

In contrast to the customization mechanisms provided by traditional programming languages, **the rules of Delta customization are very general and intuitive and are independent of the specific application implementation**. For example, when customizing the database dialect used by the ORM engine, if we want to extend the built-in MySQLDialect of the Hibernate framework, we must have some knowledge of the Hibernate framework. Then, we also need to know how Spring encapsulates Hibernate and where to find Dialect and configure it to the current SessionFactory. In the Nop platform, we only need to add the file `/_vfs/default/nop/dao/dialect/mysql.dialect.xml` to ensure that all places using the MySQL dialect are updated to use the new Dialect model.

Delta custom code is stored in a separate directory, which can be separated from the main application's code. For instance, the delta customization file is bundled into the nop-platform-delta module, and when this customization is required, the corresponding module is imported. We can also introduce multiple delta directories simultaneously and control the order of the delta layers via the nop.core.vfs.delta-layer-ids parameter. For example, configuring nop.core.vfs.delta-layer-ids=base,hunan enables two delta layers: one for the base product and another above it for a specific deployment version. This allows us to productize software at a very low cost:
- When a basic product with essentially complete functions is implemented by various customers, the basic product's code remains completely unmodified, while only Delta customization code needs to be added.
- By leveraging this mechanism during development, platform bugs can be fixed or platform functionality enhanced. For example, the app-mall project adds more field control support by customizing `/_delta/default/nop/web/xlib/control.xlib` . Specifically, if a control `<edit-string-array>` is added, as long as the data domain of the field is set to string-array in the Excel data model, the front-end interface will automatically use the input-array control from AMIS to edit the field.

For more detailed information, refer to [xdsl.md](../dev-guide/xlang/xdsl.md).

## 9. GraalVM Native Compilation

GraalVM is the next-generation Java virtual machine developed by Oracle Corporation, which supports Python, JavaScript, R, and other languages for execution. It can compile Java bytecode into binary machine code to run directly as exe files, eliminating the dependence on JDK. Native executables start faster (potentially tens of times faster), consume less CPU and memory, and occupy less disk space.

The Nop platform further simplifies GraalVM support based on the Quarkus framework and enables easy compilation of application modules into native executables.

1. The Quarkus framework itself adapts many third-party libraries to GraalVM
2. The Nop platform analyzes IoC container configuration, identifies all dynamically created beans, and generates GraalVM configuration.
3. All reflection operations in the Nop platform are performed via the ReflectionManager helper class, which logs all reflection operations. When running in debug mode, GraalVM configuration is automatically generated to the `src/resources/META-INF/native-image` directory based on collected reflection information.

For example, in the app-mall project, the following steps are required to compile a native executable: ([Requires prior installation of the GraalVM environment](https://blog.csdn.net/wangpaiblog/article/details/122850438))

```java
cd app-mall-app
mvn package -Pnative
```

The result is `target/app-mall-app-1.0-SNAPSHOT-runner.exe`. Currently, the size of the exe is relatively large (146m), primarily because the graalvm.js engine occupies nearly 60m. If dynamic JS packaging is unnecessary, the dependency on the nop-js module can be removed.

> The nop-js module should only be used during the debugging phase to execute dynamic code. In principle, you only need to use the generated static JS file when the system is running.

## Summary

The built-in delta software production line of the Nop platform is illustrated in the following figure:

![](delta-pipeline.png)

This can be expressed by the following formula:

$$
\begin{aligned}
XPage &=  XExtends\langle XView\rangle  + \Delta XPage\\
XView &= XGen\langle XMeta\rangle + \Delta XView \\
XMeta &= XGen\langle ORM \rangle + \Delta XMeta \\
ORM   &= XGen\langle ExcelModel \rangle + \Delta ORM\ \  \\
GraphQL &= Builder\langle XMeta\rangle + BizModel\\
\end{aligned}
$$


Each step of the entire inference relationship is optional: \*\* We can start directly from any step, or we can completely discard all the information inferred from the previous step. \*\*. For example, we can manually add an xview model without requiring it to have specific xmeta support, or we can directly create a new page. Yaml file and write JSON code according to the AMIS component specification. The ability of the AMIS framework will not be limited by the reasoning pipeline at all.

In daily development, we often encounter similarities and \*\* Imprecise derivative relation \*\* between some logical structures, such as the close relationship between the back-end data model and the front-end page. For the simplest case, we can directly deduce the corresponding CRUD page according to the data model. Or the database storage structure is reversely obtained by deducing from the form field information. However, this imprecise derivative relationship is difficult to be captured and utilized by existing technical means. If some association rules are forcibly agreed upon, they can only be applied to very limited specific scenarios, and will also lead to incompatibility with other technical means. It is difficult to reuse existing tools and technologies, and it is also difficult to adapt to the dynamic evolution of requirements from simple to complex.

The Nop platform provides a standard technical route for realizing the dynamic similarity-oriented reuse based on reversible computation theory:

1. With embedded metaprogramming and code generation, \*\* An inference pipeline can be established between any structure A and C. \*\*

2. ** The reasoning pipeline is broken down into steps: A =\> B =\> C **

3. ** The inference pipeline difference is further quantified. **：A =\> `_B` =\> B =\> `_C` =\> C

4. ** Each link allows temporary storage and transparent transmission of extended information ** that is not required in this step

Specifically, the chain of logical reasoning from back-end to front-end can be decomposed into four main models:

1. **ORM**: Domain model for the storage layer
2. **XMeta**: For the domain model of the GraphQL interface layer, the type definition of GraphQL can be automatically generated.
3. **XView**: Front-end logic understood at a business level using a small number of UI elements such as forms, tables, buttons, etc., independent of the front-end framework
4. **XPage**: A page model that uses a front-end framework
<#--
  Copyright (C) 2006-2017 Talend Inc. - www.talend.com
   Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->
//
// This class was generated by talend component maven plugin
//
<#macro visitParameter params>
  <#if definition.params??>
  <#list parameters as parameter>
  // todo: type handling and nested props handling
  public Property<String> ${parameter.name} = PropertyFactory.newString("${parameter.name}");

   <@visitParameter parameters.nestedParameters />
  </#list>
  </#if>
</#macro>

<#if definition.packageName?has_content>
package ${definition.packageName};
</#if>

import org.talend.components.api.properties.ComponentPropertiesImpl;
import org.talend.daikon.properties.presentation.Form;
import org.talend.daikon.properties.property.Property;

public class ${definition.propertiesClassName} extends ComponentPropertiesImpl {
    <#list parameters as parameter>
    // todo: type handling and nested props handling
    public Property<String> ${parameter.name} = PropertyFactory.newString("${parameter.name}");
    </#list>

    public ${definition.propertiesClassName}(final String name) {
        super(name);
    }

    @Override
    public void setupLayout() {
        super.setupLayout();

        final Form main = new Form(this, Form.MAIN);
        // main.addRow(connection.getForm(Form.REFERENCE));
        // main.addRow(table.getForm(Form.REFERENCE));
    }
}

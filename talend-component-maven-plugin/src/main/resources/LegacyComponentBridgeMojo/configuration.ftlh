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
final java.util.Map<String, String> configuration_<%=cid%> = new java.util.HashMap<>();
<%
    final java.util.Collection<String> configurationKeys = new java.util.HashSet<String>();
    <#list configurationKeys as key>
    configurationKeys.add("${key}");
    </#list>

    final List<IElementParameter> params = List.class.cast(node.getElementParametersWithChildrens());
    if (params != null && !params.isEmpty()) {
        for (final IElementParameter p : params) {
            if (!configurationKeys.contains(p.getName())) {
                continue;
            }
%>
            configuration_<%=cid%>.put(
                "<%=p.getName().replace('$', '.')%>",
                "<%=p.getFieldType() == EParameterFieldType.PASSWORD ? ElementParameterParser.getPasswordValue(node, p.getVariableName()) : ElementParameterParser.getStringElementParameterValue(p)%>");
<%
        }
    }
%>

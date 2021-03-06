/**
 * Copyright (C) 2006-2017 Talend Inc. - www.talend.com
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talend.sdk.component.server.front;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.container.Container;
import org.talend.sdk.component.design.extension.DesignModel;
import org.talend.sdk.component.runtime.manager.ComponentFamilyMeta;
import org.talend.sdk.component.runtime.manager.ComponentManager;
import org.talend.sdk.component.runtime.manager.ContainerComponentRegistry;
import org.talend.sdk.component.runtime.manager.ParameterMeta;
import org.talend.sdk.component.runtime.manager.reflect.parameterenricher.ActionParameterEnricher;
import org.talend.sdk.component.server.front.base.internal.RequestKey;
import org.talend.sdk.component.server.front.model.ActionReference;
import org.talend.sdk.component.server.front.model.ComponentDetail;
import org.talend.sdk.component.server.front.model.ComponentDetailList;
import org.talend.sdk.component.server.front.model.ComponentId;
import org.talend.sdk.component.server.front.model.ComponentIndex;
import org.talend.sdk.component.server.front.model.ComponentIndices;
import org.talend.sdk.component.server.front.model.ErrorDictionary;
import org.talend.sdk.component.server.front.model.Icon;
import org.talend.sdk.component.server.front.model.Link;
import org.talend.sdk.component.server.front.model.error.ErrorPayload;
import org.talend.sdk.component.server.service.ComponentManagerService;
import org.talend.sdk.component.server.service.IconResolver;
import org.talend.sdk.component.server.service.LocaleMapper;
import org.talend.sdk.component.server.service.PropertiesService;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static org.talend.sdk.component.server.front.model.ErrorDictionary.COMPONENT_MISSING;
import static org.talend.sdk.component.server.front.model.ErrorDictionary.DESIGN_MODEL_MISSING;
import static org.talend.sdk.component.server.front.model.ErrorDictionary.PLUGIN_MISSING;

@Slf4j
@Path("component")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ComponentResource {

    private final ConcurrentMap<RequestKey, ComponentIndices> indicesPerRequest = new ConcurrentHashMap<>();

    @Inject
    private ComponentManager manager;

    @Inject
    private ComponentManagerService componentManagerService;

    @Inject
    private LocaleMapper localeMapper;

    @Inject
    private PropertiesService propertiesService;

    @Inject
    private IconResolver iconResolver;

    @PostConstruct
    private void setupRuntime() {
        log.info("Initializing " + getClass());

        // preload some highly used data
        getIndex("en");
    }

    @GET
    @Path("index")
    @Documentation("Returns the list of available components.")
    public ComponentIndices getIndex(@QueryParam("language") @DefaultValue("en") final String language) {
        final Locale locale = localeMapper.mapLocale(language);
        return indicesPerRequest
                .computeIfAbsent(new RequestKey(locale),
                        k -> new ComponentIndices(manager
                                .find(c -> c
                                        .execute(
                                                () -> Stream.of(c.get(ContainerComponentRegistry.class))
                                                            .flatMap(registry -> registry.getComponents().values().stream())
                                                            .flatMap(component -> Stream.concat(
                                                                    component.getPartitionMappers().values().stream()
                                                                             .map(mapper -> toComponentIndex(c.getLoader(),
                                                                                     locale,
                                                                                     c.getId(), mapper)),
                                                                    component.getProcessors().values().stream()
                                                                             .map(proc -> toComponentIndex(c.getLoader(), locale,
                                                                                     c.getId(), proc))))))
                                .collect(toList())));
    }

    @GET
    @Path("icon/family/{id}")
    @Documentation("Returns a particular family icon in raw bytes.")
    public Response familyIcon(@PathParam("id") final String id) {
        // todo: add caching if SvgIconResolver becomes used a lot - not the case ATM
        final ComponentFamilyMeta meta = componentManagerService.findFamilyMetaById(id);
        if (meta == null) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity(new ErrorPayload(ErrorDictionary.COMPONENT_MISSING, "No family for identifier: " + id))
                           .type(APPLICATION_JSON_TYPE).build();
        }
        final IconResolver.Icon iconContent = iconResolver
                .resolve(manager.findPlugin(meta.getPlugin()).get().getLoader(), meta.getIcon());
        if (iconContent == null) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity(new ErrorPayload(ErrorDictionary.ICON_MISSING, "No icon for family identifier: " + id))
                           .type(APPLICATION_JSON_TYPE).build();
        }
        return Response.ok(iconContent.getBytes()).type(iconContent.getType()).build();
    }

    @GET
    @Path("icon/{id}")
    @Documentation("Returns a particular component icon in raw bytes.")
    public Response icon(@PathParam("id") final String id) {
        // todo: add caching if SvgIconResolver becomes used a lot - not the case ATM
        final ComponentFamilyMeta.BaseMeta<Object> meta = componentManagerService.findMetaById(id);
        if (meta == null) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity(new ErrorPayload(ErrorDictionary.COMPONENT_MISSING, "No component for identifier: " + id))
                           .type(APPLICATION_JSON_TYPE).build();
        }
        final IconResolver.Icon iconContent = iconResolver
                .resolve(manager.findPlugin(meta.getParent().getPlugin()).get().getLoader(), meta.getIcon());
        if (iconContent == null) {
            return Response.status(Response.Status.NOT_FOUND)
                           .entity(new ErrorPayload(ErrorDictionary.ICON_MISSING, "No icon for identifier: " + id))
                           .type(APPLICATION_JSON_TYPE).build();
        }
        return Response.ok(iconContent.getBytes()).type(iconContent.getType()).build();
    }

    @POST
    @Path("migrate/{id}/{configurationVersion}")
    @Documentation("Allows to migrate a component configuration without calling any component execution.")
    public Map<String, String> migrate(@PathParam("id") final String id, @PathParam("configurationVersion") final int version,
            final Map<String, String> config) {
        return componentManagerService.findMetaById(id).getMigrationHandler().migrate(version, config);
    }

    @GET // TODO: max ids.length
    @Path("details") // bulk mode to avoid to fetch components one by one when reloading a pipeline/job
    @Documentation("Returns the set of metadata about a few components identified by their 'id'.")
    public ComponentDetailList getDetail(@QueryParam("language") @DefaultValue("en") final String language,
            @QueryParam("identifiers") final String[] ids) {
        final Map<String, ErrorPayload> errors = new HashMap<>();
        final ComponentDetailList details = new ComponentDetailList(
                Stream.of(ids).map(id -> ofNullable(componentManagerService.findMetaById(id)).orElseGet(() -> {
                    errors.put(id, new ErrorPayload(COMPONENT_MISSING, "No component '" + id + "'"));
                    return null;
                })).filter(Objects::nonNull).map(meta -> {
                    final Optional<Container> plugin = manager.findPlugin(meta.getParent().getPlugin());
                    if (!plugin.isPresent()) {
                        errors.put(meta.getId(),
                                new ErrorPayload(PLUGIN_MISSING, "No plugin '" + meta.getParent().getPlugin() + "'"));
                    }
                    final Container container = plugin.get();
                    final Locale locale = localeMapper.mapLocale(language);
                    final DesignModel model = ofNullable(meta.get(DesignModel.class)).orElseGet(() -> {
                        errors.put(meta.getId(),
                                new ErrorPayload(DESIGN_MODEL_MISSING, "No design model '" + meta.getId() + "'"));
                        return new DesignModel("dummyId", emptyList(), emptyList());
                    });

                    return new ComponentDetail(
                            new ComponentId(meta.getId(), meta.getParent().getPlugin(), meta.getParent().getName(),
                                    meta.getName()),
                            meta.findBundle(container.getLoader(), locale).displayName().orElse(meta.getName()), meta.getIcon(),
                            ComponentFamilyMeta.ProcessorMeta.class.isInstance(meta) ? "processor"
                                    : "input"/* PartitionMapperMeta */,
                            meta.getVersion(),
                            propertiesService.buildProperties(meta.getParameterMetas(), container.getLoader(), locale)
                                             .collect(toList()),
                            findActions(meta.getParent().getName(),
                                    toStream(meta.getParameterMetas()).flatMap(p -> p.getMetadata().entrySet().stream())
                                                                      .filter(e -> e.getKey().startsWith(
                                                                              ActionParameterEnricher.META_PREFIX)).collect(
                                            toMap(e -> e.getKey().substring(ActionParameterEnricher.META_PREFIX.length()),
                                                    Map.Entry::getValue)),
                                    container, locale),
                            model.getInputFlows(),
                            model.getOutputFlows(),
                            /* todo? */emptyList());
                }).collect(toList()));
        if (!errors.isEmpty()) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(errors).build());
        }
        return details;

    }

    private Stream<ParameterMeta> toStream(final Collection<ParameterMeta> parameterMetas) {
        return Stream.concat(parameterMetas.stream(),
                parameterMetas.stream().map(ParameterMeta::getNestedParameters).filter(Objects::nonNull).flatMap(this::toStream));
    }

    private Collection<ActionReference> findActions(final String family, final Map<String, String> actions,
            final Container container, final Locale locale) {
        final ContainerComponentRegistry registry = container.get(ContainerComponentRegistry.class);
        return registry.getServices().stream().flatMap(s -> s.getActions().stream()).filter(s -> s.getFamily().equals(family))
                       .filter(s -> actions.containsKey(s.getType()) && actions.get(s.getType()).equals(s.getAction()))
                       .map(s -> new ActionReference(s.getFamily(), s.getAction(), s.getType(),
                               propertiesService.buildProperties(s.getParameters(), container.getLoader(), locale)
                                                .collect(toList())))
                       .collect(toList());
    }

    private ComponentIndex toComponentIndex(final ClassLoader loader, final Locale locale, final String plugin,
            final ComponentFamilyMeta.BaseMeta meta) {
        final String icon = meta.getIcon();
        final String familyIcon = meta.getParent().getIcon();
        final IconResolver.Icon iconContent = iconResolver.resolve(loader, icon);
        final IconResolver.Icon iconFamilyContent = iconResolver.resolve(loader, familyIcon);
        return new ComponentIndex(new ComponentId(meta.getId(), plugin, meta.getParent().getName(), meta.getName()),
                meta.findBundle(loader, locale).displayName().orElse(meta.getName()),
                new Icon(icon, iconContent == null ? null : iconContent.getType(),
                        iconContent == null ? null : iconContent.getBytes()),
                new Icon(familyIcon, iconFamilyContent == null ? null : iconFamilyContent.getType(),
                        iconFamilyContent == null ? null : iconFamilyContent.getBytes()),
                meta.getVersion(), meta.getParent().getCategories(),
                singletonList(new Link("Detail", "/component/details?identifiers=" + meta.getId(), MediaType.APPLICATION_JSON)));
    }
}

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
package org.talend.sdk.component.runtime.manager;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.JarInputStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.apache.xbean.finder.AnnotationFinder;
import org.apache.xbean.finder.ClassFinder;
import org.apache.xbean.finder.archive.Archive;
import org.apache.xbean.finder.archive.FileArchive;
import org.apache.xbean.finder.archive.FilteredArchive;
import org.apache.xbean.finder.archive.JarArchive;
import org.apache.xbean.finder.filter.ExcludeIncludeFilter;
import org.apache.xbean.finder.filter.Filter;
import org.apache.xbean.finder.filter.Filters;
import org.talend.sdk.component.api.component.Components;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.MigrationHandler;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.input.Emitter;
import org.talend.sdk.component.api.input.PartitionMapper;
import org.talend.sdk.component.api.internationalization.Internationalized;
import org.talend.sdk.component.api.processor.Processor;
import org.talend.sdk.component.api.service.ActionType;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.dependency.Resolver;
import org.talend.sdk.component.classloader.ConfigurableClassLoader;
import org.talend.sdk.component.container.Container;
import org.talend.sdk.component.container.ContainerListener;
import org.talend.sdk.component.container.ContainerManager;
import org.talend.sdk.component.dependencies.maven.MvnDependencyListLocalRepositoryResolver;
import org.talend.sdk.component.jmx.JmxManager;
import org.talend.sdk.component.runtime.base.Delegated;
import org.talend.sdk.component.runtime.input.LocalPartitionMapper;
import org.talend.sdk.component.runtime.input.Mapper;
import org.talend.sdk.component.runtime.input.PartitionMapperImpl;
import org.talend.sdk.component.runtime.internationalization.InternationalizationServiceFactory;
import org.talend.sdk.component.runtime.manager.asm.ProxyGenerator;
import org.talend.sdk.component.runtime.manager.extension.ComponentContextImpl;
import org.talend.sdk.component.runtime.manager.processor.AdvancedProcessorImpl;
import org.talend.sdk.component.runtime.manager.processor.SubclassesCache;
import org.talend.sdk.component.runtime.manager.proxy.JavaProxyEnricherFactory;
import org.talend.sdk.component.runtime.manager.reflect.ParameterModelService;
import org.talend.sdk.component.runtime.manager.reflect.ReflectionService;
import org.talend.sdk.component.runtime.manager.service.ResolverImpl;
import org.talend.sdk.component.runtime.manager.spi.ContainerListenerExtension;
import org.talend.sdk.component.runtime.manager.xbean.KnownClassesFilter;
import org.talend.sdk.component.runtime.manager.xbean.KnownJarsFilter;
import org.talend.sdk.component.runtime.manager.xbean.NestedJarArchive;
import org.talend.sdk.component.runtime.output.data.AccessorCache;
import org.talend.sdk.component.runtime.serialization.LightContainer;
import org.talend.sdk.component.runtime.visitor.ModelListener;
import org.talend.sdk.component.runtime.visitor.ModelVisitor;
import org.talend.sdk.component.spi.component.ComponentExtension;
import org.w3c.dom.Document;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static org.apache.xbean.finder.archive.FileArchive.decode;

@Slf4j
public class ComponentManager implements AutoCloseable {

    private static final MigrationHandler NO_MIGRATION = (incomingVersion, incomingData) -> incomingData;

    private static final AtomicReference<ComponentManager> CONTEXTUAL_INSTANCE = new AtomicReference<>();

    private static final Components DEFAULT_COMPONENT = new Components() {

        @Override
        public Class<? extends Annotation> annotationType() {
            return Components.class;
        }

        @Override
        public String family() {
            return "";
        }

        @Override
        public String[] categories() {
            return new String[] { "Misc" };
        }
    };

    protected final ContainerManager container;

    // tcomp (org.talend + javax.annotation) + logging (slf4j) are/can be provided service
    // + tcomp "runtime" indeed (invisible from the components but required for the runtime
    private final Filter classesFilter = Filters.prefixes("org.talend.sdk.component.api.", "org.talend.sdk.component.spi.",
            "javax.annotation.", "org.talend.sdk.component.classloader.", "org.talend.sdk.component.runtime.", "org.slf4j.",
            "org.apache.beam.runners.", "org.apache.beam.sdk.");

    private final Filter excludeClassesFilter = Filters.prefixes("org.apache.beam.sdk.io.", "org.apache.beam.sdk.extensions.");

    private final ParameterModelService parameterModelService = new ParameterModelService();

    private final InternationalizationServiceFactory internationalizationServiceFactory = new InternationalizationServiceFactory();

    private final ProxyGenerator proxyGenerator = new ProxyGenerator();

    private final JavaProxyEnricherFactory javaProxyEnricherFactory = new JavaProxyEnricherFactory();

    // kind of extracted to ensure we can switch it later if needed
    // (like using a Spring/CDI context and something else than xbean)
    private final ReflectionService reflections = new ReflectionService(parameterModelService);

    private final Collection<ComponentExtension> extensions;

    /**
     * @param m2                   the maven repository location if on the file system.
     * @param dependenciesResource the resource path containing dependencies.
     * @param jmxNamePattern       a pattern to register the plugins (containers) in JMX, null otherwise.
     */
    public ComponentManager(final File m2, final String dependenciesResource, final String jmxNamePattern) {
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        this.container = new ContainerManager(ContainerManager.DependenciesResolutionConfiguration.builder()
                                                                                                  .resolver(
                                                                                                          new MvnDependencyListLocalRepositoryResolver(
                                                                                                                  dependenciesResource))
                                                                                                  .rootRepositoryLocation(m2)
                                                                                                  .create(),
                ContainerManager.ClassLoaderConfiguration.builder().parent(tccl)
                                                         .parentClassesFilter(this::isContainerClass)
                                                         .classesFilter(name -> !isContainerClass(name))
                                                         .supportsResourceDependencies(true).create());
        this.container.registerListener(new Updater());
        ofNullable(jmxNamePattern).map(String::trim).filter(n -> !n.isEmpty())
                                  .ifPresent(p -> this.container
                                          .registerListener(new JmxManager(p, ManagementFactory.getPlatformMBeanServer())));
        toStream(loadServiceProviders(ContainerListenerExtension.class, tccl))
                .forEach(listener -> container.registerListener(listener));
        this.extensions = toStream(loadServiceProviders(ComponentExtension.class, tccl)).collect(toList());
    }

    /**
     * Creates a default manager with default maven local repository, TALEND-INF/dependencies.txt file
     * to find the dependencies of the plugins and a default JMX pattern for plugins. It also adds the caller as a plugin.
     */
    public static ComponentManager instance() {
        ComponentManager manager = CONTEXTUAL_INSTANCE.get();
        if (manager == null) {
            synchronized (CONTEXTUAL_INSTANCE) {
                if (CONTEXTUAL_INSTANCE.get() == null) {
                    final Thread shutdownHook = new Thread(
                            ComponentManager.class.getName() + "-" + ComponentManager.class.hashCode()) {

                        @Override
                        public void run() {
                            CONTEXTUAL_INSTANCE.get().close();
                        }
                    };

                    manager = new ComponentManager(findM2(), "TALEND-INF/dependencies.txt",
                            "org.talend.sdk.component:type=component,value=%s") {

                        private final AtomicBoolean closed = new AtomicBoolean(false);

                        {

                            log.info("Creating the contextual ComponentManager instance " + getIdentifiers());
                            if (!Boolean.getBoolean("component.manager.callers.skip")) {
                                addCallerAsPlugin();
                            }

                            // common for studio until job generation is updated to build a tcomp friendly bundle
                            final String componentClasspath = System.getProperty("component.manager.classpath",
                                    System.getProperty("java.class.path"));
                            // alternatively we could capture based on TALEND-INF/dependencies.txt jars
                            if (!Boolean.getBoolean("component.manager.classpath.skip")) {
                                if (componentClasspath != null) {
                                    final String[] jars = componentClasspath.split(File.pathSeparator);
                                    if (jars.length > 1) {
                                        Stream.of(jars).map(File::new).filter(File::exists)
                                              .filter(f -> !f.isDirectory() && f.getName().endsWith(".jar"))
                                              .filter(f -> KnownJarsFilter.INSTANCE.test(f.getName()))
                                              .filter(f -> !hasPlugin(container.buildAutoIdFromName(f.getName())))
                                              .forEach(jar -> addPlugin(jar.getAbsolutePath()));
                                    }
                                }
                            }

                            container.getDefinedNestedPlugin().stream().filter(p -> !hasPlugin(p)).forEach(this::addPlugin);
                            log.info("Components: " + availablePlugins());
                        }

                        @Override
                        public void close() {
                            if (!closed.compareAndSet(false, true)) {
                                return;
                            }
                            try {
                                synchronized (CONTEXTUAL_INSTANCE) {
                                    if (CONTEXTUAL_INSTANCE.compareAndSet(this, null)) {
                                        try {
                                            Runtime.getRuntime().removeShutdownHook(shutdownHook);
                                        } catch (final IllegalStateException ise) {
                                            // already shutting down
                                        }
                                    }
                                }
                            } finally {
                                super.close();
                                log.info("Released the contextual ComponentManager instance " + getIdentifiers());
                            }
                        }

                        Object readResolve() throws ObjectStreamException {
                            return new SerializationReplacer();
                        }
                    };

                    Runtime.getRuntime().addShutdownHook(shutdownHook);
                    log.info("Created the contextual ComponentManager instance " + getIdentifiers());
                    if (!CONTEXTUAL_INSTANCE.compareAndSet(null, manager)) { // unlikely it fails in a synch block
                        manager = CONTEXTUAL_INSTANCE.get();
                    }
                }
            }
        }

        return manager;
    }

    protected static File findM2() {
        return ofNullable(System.getProperty("talend.component.manager.m2.repository")).map(File::new).orElseGet(() -> {
            // check if we
            // are in the
            // studio and
            // if so just
            // grab the the
            // studio config,
            final String m2Repo = System.getProperty("maven.repository");
            if (!"global".equals(m2Repo)) {
                final File localM2 = new File(System.getProperty("osgi.configuration.area"), ".m2");
                if (localM2.exists()) {
                    return localM2;
                }
            }
            return findDefaultM2();
        });
    }

    private static File findDefaultM2() {
        // check out settings.xml first
        final File settings = new File(System.getProperty("talend.component.manager.m2.settings",
                System.getProperty("user.home") + "/.m2/settings.xml"));
        if (settings.exists()) {
            try {
                final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                final DocumentBuilder builder = factory.newDocumentBuilder();

                final Document document = builder.parse(settings);
                final XPathFactory xpf = XPathFactory.newInstance();
                final XPath xp = xpf.newXPath();
                return new File(xp.evaluate("//settings/localRepository/text()", document.getDocumentElement()));
            } catch (final Exception ignore) {
                // fallback on default local path
            }
        }

        return new File(System.getProperty("user.home"), ".m2/repository");
    }

    private static String getIdentifiers() {
        return "(classloader=" + ComponentManager.class.getClassLoader() + ", jvm="
                + ManagementFactory.getRuntimeMXBean().getName() + ")";
    }

    private static <T> Stream<T> toStream(Iterator<T> iterator) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.IMMUTABLE), false);
    }

    private static <T> Iterator<T> loadServiceProviders(Class<T> service, ClassLoader classLoader) {
        return ServiceLoader.load(service, classLoader).iterator();
    }

    private static File toFile(final String classFileName, final URL url) {
        String path = url.getFile();
        path = path.substring(0, path.length() - classFileName.length());
        return new File(decode(path));
    }

    public void addCallerAsPlugin() {
        try {
            final ClassLoader tmpLoader = ofNullable(Thread.currentThread().getContextClassLoader())
                    .orElseGet(ClassLoader::getSystemClassLoader);
            final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
            final Class<?> jarMarker = tmpLoader.loadClass(Stream.of(stackTrace)
                                                                 .filter(c -> !c.getClassName().startsWith(
                                                                         "org.talend.sdk.component.runtime.manager.")
                                                                         && !c.getClassName().startsWith(
                                                                         "org.talend.sdk.component.runtime.standalone.")
                                                                         && !c.getClassName().startsWith(
                                                                         "org.talend.sdk.component.runtime.avro.")
                                                                         && !c.getClassName().startsWith("org.talend.daikon.")
                                                                         && !c.getClassName().startsWith("org.talend.designer.")
                                                                         && !c.getClassName().startsWith("org.eclipse.") && !c
                                                                         .getClassName().startsWith("java.")
                                                                         && !c.getClassName().startsWith("javax.") && !c
                                                                         .getClassName().startsWith("sun.")
                                                                         && !c.getClassName().startsWith("com.sun.") && !c
                                                                         .getClassName().startsWith("com.oracle."))
                                                                 .findFirst().map(StackTraceElement::getClassName)
                                                                 .orElse(ComponentManager.class.getName()));
            if (jarMarker == ComponentManager.class) {
                return;
            }

            addJarContaining(tmpLoader, jarMarker.getName().replace(".", "/") + ".class");
        } catch (final ClassNotFoundException e) {
            // ignore, not important. if we can't do it then the plugins should be registered normally
        }
    }

    protected void addJarContaining(final ClassLoader loader, final String resource) {
        final URL url = loader.getResource(resource);
        if (url != null) {
            File plugin = null;
            switch (url.getProtocol()) {
            case "bundleresource": // studio on equinox, this is the definition part so we don't register it
                break;
            case "file":
                plugin = toFile(resource, url);
                break;
            case "jar":
                if (url.getPath() != null && url.getPath().startsWith("mvn:")) { // pax mvn
                    // studio temporary integration, to drop when studio integrates correctly tcomp
                    break;
                }
                final String spec = url.getFile();
                final int separator = spec.indexOf('!');
                if (separator > 0) {
                    try {
                        plugin = new File(decode(new URL(spec.substring(0, separator)).getFile()));
                    } catch (final MalformedURLException e) {
                        // no-op
                    }
                }
                break;
            default:
            }
            if (plugin == null) {
                log.warn("Can't find " + url);
                return;
            }
            Stream.of(plugin)
                  // just a small workaround for maven/gradle
                  .flatMap(p -> "test-classes".equals(p.getName()) && p.getParentFile() != null
                          ? Stream.of(p, new File(p.getParentFile(), "classes"))
                          : Stream.of(p))
                  .filter(path -> !container.find(path.getName()).isPresent()).forEach(p -> ofNullable(p).ifPresent(file -> {
                final String id = addPlugin(file.getAbsolutePath());
                if (container.find(id).get().get(ContainerComponentRegistry.class).getComponents().isEmpty()) {
                    removePlugin(id);
                }
            }));
        }
    }

    public <T> Stream<T> find(final Function<Container, Stream<T>> mapper) {
        return container.findAll().stream().flatMap(mapper);
    }

    // really a DIY entry point for custom flow, it creates component instances but nothing more
    public Optional<Object> createComponent(final String plugin, final String name, final ComponentType componentType,
            final int version, final Map<String, String> configuration) {
        return find(pluginContainer ->
                Stream.of(pluginContainer.get(ContainerComponentRegistry.class)))
                .filter(Objects::nonNull)
                .map(r -> r.getComponents().get(container.buildAutoIdFromName(plugin)))
                .filter(Objects::nonNull)
                .map(component -> ofNullable(componentType.findMeta(component).get(name))
                        .map(comp -> comp.getInstantiator().apply(configuration == null ? null : comp.getMigrationHandler()
                                                                                                     .migrate(version,
                                                                                                             configuration))))
                .findFirst()
                .flatMap(identity())
                // unwrap to access the actual instance which is the desired one
                .filter(Delegated.class::isInstance)
                .map(i -> Delegated.class
                        .cast(i)
                        .getDelegate());
    }

    public Optional<Mapper> findMapper(final String plugin, final String name, final int version,
            final Map<String, String> configuration) {
        return find(pluginContainer -> Stream.of(
                pluginContainer.get(ContainerComponentRegistry.class).getComponents().get(container.buildAutoIdFromName(plugin))))
                .filter(Objects::nonNull)
                .map(component -> ofNullable(component.getPartitionMappers().get(name))
                        .map(mapper -> mapper.getInstantiator()
                                             .apply(configuration == null ? null
                                                     : mapper.getMigrationHandler().migrate(version, configuration)))
                        .map(Mapper.class::cast))
                .findFirst().flatMap(identity());
    }

    public Optional<org.talend.sdk.component.runtime.output.Processor> findProcessor(final String plugin, final String name,
            final int version, final Map<String, String> configuration) {
        return find(
                pluginContainer -> Stream
                        .of(pluginContainer.get(ContainerComponentRegistry.class).getComponents()
                                           .get(container.buildAutoIdFromName(plugin))))
                .filter(Objects::nonNull).map(
                        component -> ofNullable(component.getProcessors().get(name))
                                .map(proc -> proc.getInstantiator()
                                                 .apply(configuration == null ? null
                                                         : proc.getMigrationHandler().migrate(version,
                                                         configuration)))
                                .map(org.talend.sdk.component.runtime.output.Processor.class::cast))
                .findFirst().flatMap(identity());
    }

    public boolean hasPlugin(final String plugin) {
        return container.find(plugin).isPresent();
    }

    public Optional<Container> findPlugin(final String plugin) {
        return container.find(plugin);
    }

    public String addPlugin(final String pluginRootFile) {
        final String id = container.create(pluginRootFile).getId();
        log.info("Adding plugin: " + pluginRootFile + ", as " + id);
        return id;
    }

    protected String addPlugin(final String forcedId, final String pluginRootFile) {
        final String id = container.create(forcedId, pluginRootFile).getId();
        log.info("Adding plugin: " + pluginRootFile + ", as " + id);
        return id;
    }

    public void removePlugin(final String id) {
        container.find(id).ifPresent(Container::close);
        log.info("Removed plugin: " + id);
    }

    protected boolean isContainerClass(final String name) {
        // workaround until beam is able to have a consistent packaging
        if (excludeClassesFilter.accept(name)) {
            // check if it is beam-sdks-java-core, if so then it is considered as a container class
            final URL resource = ComponentManager.class.getClassLoader().getResource(name.replace('.', '/') + ".class");
            if (resource != null) {
                if (resource.getFile().startsWith("mvn:org.apache.beam/beam-sdks-java-core/")) { // studio
                    return true;
                } else if ("jar".equals(resource.getProtocol())) { // standalone
                    final String file = resource.getFile();
                    final int separator = file.indexOf('!');
                    if (separator > 0) {
                        try {
                            return new File(decode(new URL(file.substring(0, separator)).getFile())).getName()
                                                                                                    .startsWith(
                                                                                                            "beam-sdks-java-core-");

                        } catch (final MalformedURLException e) {
                            // let it return false
                        }
                    }
                }
            }
            return false;
        }
        return name != null && classesFilter.accept(name);
    }

    @Override
    public void close() {
        container.close();
    }

    private <T> T executeInContainer(final String plugin, final Supplier<T> supplier) {
        final Thread thread = Thread.currentThread();
        final ClassLoader old = thread.getContextClassLoader();
        thread.setContextClassLoader(container.find(plugin).map(Container::getLoader).map(ClassLoader.class::cast).orElse(old));
        try {
            return supplier.get();
        } finally {
            thread.setContextClassLoader(old);
        }
    }

    public List<String> availablePlugins() {
        return container.findAll().stream().map(Container::getId).collect(toList());
    }

    protected void containerServices(final Container container, final Map<Class<?>, Object> services) {
        services.put(ProxyGenerator.class, proxyGenerator);
        services.put(AccessorCache.class, new AccessorCache(container.getId()));
        services.put(Resolver.class, new ResolverImpl(container.getId(), container.getLocalDependencyRelativeResolver()));
        services.put(SubclassesCache.class,
                new SubclassesCache(container.getId(), proxyGenerator, container.getLoader(), new ConcurrentHashMap<>()));
    }

    private <T extends Annotation> T findComponentsConfig(final Map<String, AnnotatedElement> componentDefaults,
            final Class<?> type, final ConfigurableClassLoader loader, final Class<T> annotation, final T defaultValue) {

        final AnnotatedElement annotatedElement = componentDefaults.computeIfAbsent(getAnnotatedElementCacheKey(type), p -> {
            if (p != null) {
                String currentPackage = p;
                do {
                    try {
                        final Class<?> pckInfo = loader.loadClass(currentPackage + ".package-info");
                        if (pckInfo.isAnnotationPresent(annotation)) {
                            return pckInfo;
                        }
                    } catch (final ClassNotFoundException e) {
                        // no-op
                    }

                    final int endPreviousPackage = currentPackage.lastIndexOf('.');
                    if (endPreviousPackage < 0) { // we don't accept default package since it is not specific enough
                        break;
                    }

                    currentPackage = currentPackage.substring(0, endPreviousPackage);
                } while (true);
            }

            return new AnnotatedElement() {

                @Override
                public <T extends Annotation> T getAnnotation(final Class<T> annotationClass) {
                    return annotationClass == annotation ? annotationClass.cast(defaultValue) : null;
                }

                @Override
                public Annotation[] getAnnotations() {
                    return new Annotation[] { defaultValue };
                }

                @Override
                public Annotation[] getDeclaredAnnotations() {
                    return getAnnotations();
                }
            };
        });
        return annotatedElement.getAnnotation(annotation);
    }

    private String getAnnotatedElementCacheKey(final Class<?> type) {
        return ofNullable(type.getPackage().getName()).orElse("");
    }

    private Class<?> enforceSerializable(final Container container, final Class<?> type) {
        if (Serializable.class.isAssignableFrom(type)) {
            return type;
        }
        try {
            type.getMethod("writeReplace");
            return type;
        } catch (final NoSuchMethodException e) {
            // no-op, let's try to generate a proxy
        }
        return container
                .execute(() -> proxyGenerator.generateProxy(container.getLoader(), type, container.getId(), type.getName()));
    }

    private Function<Map<String, String>, Object[]> createParametersFactory(final String plugin, final Executable method,
            final Map<Class<?>, Object> services) {
        return executeInContainer(plugin, () -> reflections.parameterFactory(method, services));
    }

    public enum ComponentType {
        MAPPER {
            @Override
            Map<String, ? extends ComponentFamilyMeta.BaseMeta> findMeta(final ComponentFamilyMeta family) {
                return family.getPartitionMappers();
            }
        },
        PROCESSOR {
            @Override
            Map<String, ? extends ComponentFamilyMeta.BaseMeta> findMeta(final ComponentFamilyMeta family) {
                return family.getProcessors();
            }
        };

        abstract Map<String, ? extends ComponentFamilyMeta.BaseMeta> findMeta(ComponentFamilyMeta family);
    }

    @AllArgsConstructor
    private static class SerializationReplacer implements Serializable {

        Object readResolve() throws ObjectStreamException {
            return instance();
        }
    }

    @Data
    @AllArgsConstructor
    public static class AllServices {

        private final Map<Class<?>, Object> services;
    }

    private class Updater implements ContainerListener {

        private final ModelVisitor visitor = new ModelVisitor();

        @Override
        public void onCreate(final Container container) {
            final ConfigurableClassLoader loader = container.getLoader();
            final AnnotationFinder finder;
            Archive archive = null;
            try {
                /*
                 * container.findExistingClasspathFiles() - we just scan the root module for now,
                 * no need to scan all the world
                 */
                archive = Optional.of(container.getRootModule()).map(f -> toArchive(container, loader, f))
                                  .orElseThrow(() -> new IllegalArgumentException(
                                          "Unsupported scanning on " + container.getRootModule()
                                                  + ", since it is neither a file nor a jar."));

                // undocumented scanning config for now since we would document it only if proven useful
                Filter filter = KnownClassesFilter.INSTANCE;
                try (final InputStream containerFilterConfig = container.getLoader()
                                                                        .getResourceAsStream("TALEND-INF/scanning.properties")) {
                    if (containerFilterConfig != null) {
                        final Properties config = new Properties();
                        config.load(containerFilterConfig);
                        final Filter accept = ofNullable(config.getProperty("classloader.includes")).map(String::trim)
                                                                                                    .filter(v -> !v.isEmpty())
                                                                                                    .map(s -> s.split(","))
                                                                                                    .map(Filters::patterns)
                                                                                                    .orElseGet(
                                                                                                            () -> name -> true);
                        final Filter reject = ofNullable(config.getProperty("classloader.excludes")).map(String::trim)
                                                                                                    .filter(v -> !v.isEmpty())
                                                                                                    .map(s -> s.split(","))
                                                                                                    .map(Filters::patterns)
                                                                                                    .orElseGet(
                                                                                                            () -> name -> false);
                        filter = new ExcludeIncludeFilter(accept, reject);
                    }
                } catch (final IOException e) {
                    log.debug(e.getMessage(), e);
                }
                finder = new AnnotationFinder(new FilteredArchive(archive, filter));
            } finally {
                if (AutoCloseable.class.isInstance(archive)) {
                    try {
                        AutoCloseable.class.cast(archive).close();
                    } catch (final Exception e) {
                        log.warn(e.getMessage());
                    }
                }
            }
            final ContainerComponentRegistry registry = new ContainerComponentRegistry();
            container.set(ContainerComponentRegistry.class, registry);

            final Map<Class<?>, Object> services = new HashMap<>();
            container.set(AllServices.class, new AllServices(services));
            // container services
            containerServices(container, services);

            container.set(LightContainer.class, new LightContainer() {

                @Override
                public ClassLoader classloader() {
                    return container.getLoader();
                }

                @Override
                public <T> T findService(final Class<T> key) {
                    return key.cast(services.get(key));
                }
            });

            final Map<String, AnnotatedElement> componentDefaults = new HashMap<>();

            finder.findAnnotatedClasses(Internationalized.class).forEach(proxy -> {
                final Object instance = javaProxyEnricherFactory.asSerializable(container.getLoader(), container.getId(),
                        proxy.getName(), internationalizationServiceFactory.create(proxy, container.getLoader()));
                services.put(proxy, instance);
                registry.getServices().add(new ServiceMeta(instance, emptyList()));
            });
            finder.findAnnotatedClasses(Service.class).forEach(service -> {
                try {
                    final Object instance;
                    final Thread thread = Thread.currentThread();
                    final ClassLoader old = thread.getContextClassLoader();
                    thread.setContextClassLoader(container.getLoader());
                    try {
                        instance = enforceSerializable(container, service).getConstructor().newInstance();
                        doInvoke(container.getId(), instance, PostConstruct.class);
                    } catch (final InstantiationException | IllegalAccessException e) {
                        throw new IllegalArgumentException(e);
                    } catch (final InvocationTargetException e) {
                        throw new IllegalArgumentException(e.getTargetException());
                    } finally {
                        thread.setContextClassLoader(old);
                    }
                    services.put(service, instance);
                    registry.getServices()
                            .add(new ServiceMeta(instance,
                                    Stream.of(service.getMethods())
                                          .filter(m -> Stream.of(m.getAnnotations())
                                                             .anyMatch(a -> a.annotationType()
                                                                             .isAnnotationPresent(ActionType.class)))
                                          .map(serviceMethod -> {
                                              final Components components = findComponentsConfig(componentDefaults,
                                                      serviceMethod.getDeclaringClass(), container.getLoader(),
                                                      Components.class, DEFAULT_COMPONENT);

                                              final Annotation marker = Stream.of(serviceMethod.getAnnotations())
                                                                              .filter(a -> a.annotationType().isAnnotationPresent(
                                                                                      ActionType.class))
                                                                              .findFirst()
                                                                              .orElseThrow(() -> new IllegalStateException(
                                                                                      "Something went wrong with "
                                                                                              + serviceMethod));
                                              final ActionType actionType = marker.annotationType()
                                                                                  .getAnnotation(ActionType.class);

                                              if (actionType.expectedReturnedType() != Object.class && !actionType
                                                      .expectedReturnedType().isAssignableFrom(serviceMethod.getReturnType())) {
                                                  throw new IllegalArgumentException("Can't use " + marker + " on "
                                                          + serviceMethod + ", expected returned type: "
                                                          + actionType.expectedReturnedType() + ", actual one: "
                                                          + serviceMethod.getReturnType());
                                              }

                                              final String component;
                                              try {
                                                  component = ofNullable(String.class
                                                          .cast(marker.annotationType().getMethod("family").invoke(marker)))
                                                          .filter(c -> !c.isEmpty()).orElseGet(components::family);
                                                  if (component.isEmpty()) {
                                                      throw new IllegalArgumentException("No component for " + serviceMethod
                                                              + ", maybe add a @Components on your package "
                                                              + service.getDeclaringClass().getPackage());
                                                  }
                                              } catch (final NoSuchMethodException | IllegalAccessException e) {
                                                  throw new IllegalStateException(e);
                                              } catch (final InvocationTargetException e) {
                                                  throw new IllegalStateException(e.getTargetException());
                                              }
                                              final String name = Stream.of("name", "value").map(mName -> {
                                                  try {
                                                      return String.class
                                                              .cast(marker.annotationType().getMethod(mName).invoke(marker));
                                                  } catch (final IllegalAccessException e) {
                                                      throw new IllegalStateException(e);
                                                  } catch (final InvocationTargetException e) {
                                                      throw new IllegalStateException(e.getTargetException());
                                                  } catch (final NoSuchMethodException e) {
                                                      return null;
                                                  }
                                              }).filter(Objects::nonNull).findFirst().orElse("default");

                                              final Function<Map<String, String>, Object[]> parameterFactory = createParametersFactory(
                                                      container.getId(), serviceMethod, services);
                                              final Object actionInstance = Modifier.isStatic(serviceMethod.getModifiers())
                                                      ? null
                                                      : instance;
                                              final Function<Map<String, String>, Object> invoker = arg -> executeInContainer(
                                                      container.getId(), () -> {
                                                          try {
                                                              final Object[] args = parameterFactory.apply(arg);
                                                              return serviceMethod.invoke(actionInstance, args);
                                                          } catch (final IllegalAccessException e) {
                                                              throw new IllegalStateException(e);
                                                          } catch (final InvocationTargetException e) { // do we want to unwrap
                                                              // it?
                                                              throw new IllegalStateException(e.getTargetException());
                                                          }
                                                      });

                                              return new ServiceMeta.ActionMeta(component, actionType.value(), name,
                                                      serviceMethod.getGenericParameterTypes(),
                                                      parameterModelService.buildParameterMetas(serviceMethod,
                                                              ofNullable(serviceMethod.getDeclaringClass().getPackage())
                                                                      .map(Package::getName).orElse("")),
                                                      invoker);
                                          }).collect(toList())));
                } catch (final NoSuchMethodException e) {
                    throw new IllegalArgumentException("No default constructor for " + service);
                }

                log.info("Added @Service " + service + " for container-id=" + container.getId());
            });

            Stream.of(PartitionMapper.class, Processor.class, Emitter.class).flatMap(a -> finder.findAnnotatedClasses(a).stream())
                  .forEach(type -> {
                      final Components components = findComponentsConfig(componentDefaults, type, container.getLoader(),
                              Components.class, DEFAULT_COMPONENT);

                      final ComponentContextImpl context = new ComponentContextImpl(type);
                      extensions.forEach(e -> {
                          context.setCurrentExtension(e);
                          try {
                              e.onComponent(context);
                          } finally {
                              context.setCurrentExtension(null);
                          }
                      });

                      final ComponentMetaBuilder builder = new ComponentMetaBuilder(container.getId(), services, components,
                              componentDefaults.get(getAnnotatedElementCacheKey(type)), context);

                      final Thread thread = Thread.currentThread();
                      final ClassLoader old = thread.getContextClassLoader();
                      thread.setContextClassLoader(container.getLoader());
                      try {
                          visitor.visit(type, builder, !context.isNoValidation());
                      } finally {
                          thread.setContextClassLoader(old);
                      }

                      ofNullable(builder.component).ifPresent(c -> {
                          // for now we assume one family per module, we can remove this constraint if really needed
                          // but kind of enforce a natural modularity

                          final ComponentFamilyMeta componentFamilyMeta = registry.getComponents().computeIfAbsent(c.getName(),
                                  n -> c);
                          if (componentFamilyMeta != c) {
                              if (componentFamilyMeta.getProcessors().keySet().stream()
                                                     .anyMatch(k -> c.getProcessors().keySet().contains(k))) {
                                  throw new IllegalArgumentException("Conflicting processors in " + c);
                              }
                              if (componentFamilyMeta.getPartitionMappers().keySet().stream()
                                                     .anyMatch(k -> c.getPartitionMappers().keySet().contains(k))) {
                                  throw new IllegalArgumentException("Conflicting mappers in " + c);
                              }

                              // if we passed validations then merge
                              componentFamilyMeta.getProcessors().putAll(c.getProcessors());
                              componentFamilyMeta.getPartitionMappers().putAll(c.getPartitionMappers());
                          }
                      });

                      log.info("Parsed component " + type + " for container-id=" + container.getId());
                  });
        }

        private Archive toArchive(final Container container, final ConfigurableClassLoader loader, final String module) {
            final File file = new File(module);
            if (file.exists()) {
                if (file.isDirectory()) {
                    return new FileArchive(loader, file);
                }
                if (file.getName().endsWith(".jar")) {
                    try {
                        return new JarArchive(loader, file.toURI().toURL());
                    } catch (final MalformedURLException e) {
                        throw new IllegalArgumentException(e);
                    }
                }
            }

            log.info(container.getRootModule() + " is not a file, will try to look it up from a nested maven repository");

            final InputStream nestedJar = loader.getParent()
                                                .getResourceAsStream(ConfigurableClassLoader.NESTED_MAVEN_REPOSITORY + container
                                                        .getRootModule());
            if (nestedJar != null) {
                final JarInputStream jarStream;
                try {
                    jarStream = new JarInputStream(nestedJar);
                    log.debug("Found a nested resource for " + container.getRootModule());
                    return new NestedJarArchive(jarStream, loader);
                } catch (final IOException e) {
                    try {
                        nestedJar.close();
                    } catch (final IOException e1) {
                        // no-op
                    }
                }
            }

            log.warn("Didn't find " + container.getRootModule());
            return null;
        }

        @Override
        public void onClose(final Container container) {
            // ensure we don't keep any data/ref after the classloader of the container is released
            ofNullable(container.get(ContainerComponentRegistry.class)).ifPresent(r -> {
                final ContainerComponentRegistry registry = container.remove(ContainerComponentRegistry.class);
                registry.getComponents().clear();
                registry.getServices().forEach(s -> doInvoke(container.getId(), s.getInstance(), PreDestroy.class));
                registry.getServices().clear();
            });
        }

        private void doInvoke(final String container, final Object instance, final Class<? extends Annotation> marker) {
            executeInContainer(container, () -> {
                final Class<?> instanceClass = instance.getClass();
                new ClassFinder(instanceClass.getName().contains("$$") ? instanceClass.getSuperclass() : instanceClass)
                        .findAnnotatedMethods(marker).stream() // we don't limit to one for now
                        .filter(m -> Modifier.isPublic(m.getModifiers())).forEach(m -> {
                    try {
                        m.invoke(instance);
                    } catch (final IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    } catch (final InvocationTargetException e) {
                        throw new IllegalStateException(e.getTargetException());
                    }
                });
                return null;
            });
        }
    }

    @RequiredArgsConstructor
    private class ComponentMetaBuilder implements ModelListener {

        private final String plugin;

        private final Map<Class<?>, Object> services;

        private final Components components;

        private final AnnotatedElement familyAnnotationElement;

        private final ComponentContextImpl context;

        private ComponentFamilyMeta component;

        @Override
        public void onPartitionMapper(final Class<?> type, final PartitionMapper partitionMapper) {
            final Constructor<?> constructor = findConstructor(type);
            final Function<Map<String, String>, Object[]> parameterFactory = createParametersFactory(plugin, constructor,
                    services);
            final String name = of(partitionMapper.name()).filter(n -> !n.isEmpty()).orElseGet(type::getName);
            final ComponentFamilyMeta component = getOrCreateComponent(partitionMapper.family());

            final Function<Map<String, String>, Mapper> instantiator = context.getOwningExtension() != null
                    && context.getOwningExtension().supports(Mapper.class)
                    ? config -> executeInContainer(plugin, () -> context.getOwningExtension()
                                                                        .convert(new ComponentInstanceImpl(doInvoke(constructor,
                                                                                parameterFactory.apply(config)),
                                                                                plugin, component.getName(), name), Mapper.class))
                    : config -> new PartitionMapperImpl(component.getName(), name, null, plugin,
                    partitionMapper.infinite(), doInvoke(constructor, parameterFactory.apply(config)));

            component.getPartitionMappers().put(name,
                    new ComponentFamilyMeta.PartitionMapperMeta(component, name, findIcon(type), findVersion(type), type,
                            parameterModelService.buildParameterMetas(constructor, getPackage(type)), instantiator,
                            findMigrationHandler(type), !context.isNoValidation()));
        }

        @Override
        public void onEmitter(final Class<?> type, final Emitter emitter) {
            final Constructor<?> constructor = findConstructor(type);
            final Function<Map<String, String>, Object[]> parameterFactory = createParametersFactory(plugin, constructor,
                    services);
            final String name = of(emitter.name()).filter(n -> !n.isEmpty()).orElseGet(type::getName);
            final ComponentFamilyMeta component = getOrCreateComponent(emitter.family());
            final Function<Map<String, String>, Mapper> instantiator = context.getOwningExtension() != null
                    && context.getOwningExtension().supports(Mapper.class)
                    ? config -> executeInContainer(plugin, () -> context.getOwningExtension()
                                                                        .convert(new ComponentInstanceImpl(doInvoke(constructor,
                                                                                parameterFactory.apply(config)),
                                                                                plugin, component.getName(), name), Mapper.class))
                    : config -> new LocalPartitionMapper(component.getName(), name, plugin,
                    doInvoke(constructor, parameterFactory.apply(config)));
            component.getPartitionMappers().put(name,
                    new ComponentFamilyMeta.PartitionMapperMeta(component, name, findIcon(type), findVersion(type), type,
                            parameterModelService.buildParameterMetas(constructor, getPackage(type)), instantiator,
                            findMigrationHandler(type), !context.isNoValidation()));
        }

        @Override
        public void onProcessor(final Class<?> type, final Processor processor) {
            final Constructor<?> constructor = findConstructor(type);
            final Function<Map<String, String>, Object[]> parameterFactory = createParametersFactory(plugin, constructor,
                    services);
            final String name = of(processor.name()).filter(n -> !n.isEmpty()).orElseGet(type::getName);
            final ComponentFamilyMeta component = getOrCreateComponent(processor.family());
            final Function<Map<String, String>, org.talend.sdk.component.runtime.output.Processor> instantiator = context
                    .getOwningExtension() != null
                    && context.getOwningExtension().supports(org.talend.sdk.component.runtime.output.Processor.class)
                    ? config -> executeInContainer(plugin,
                    () -> context.getOwningExtension().convert(
                            new ComponentInstanceImpl(doInvoke(constructor, parameterFactory.apply(config)),
                                    plugin, component.getName(), name),
                            org.talend.sdk.component.runtime.output.Processor.class))
                    : config -> new AdvancedProcessorImpl(this.component.getName(), name, plugin,
                    doInvoke(constructor, parameterFactory.apply(config)));
            component.getProcessors().put(name,
                    new ComponentFamilyMeta.ProcessorMeta(component, name, findIcon(type), findVersion(type), type,
                            parameterModelService.buildParameterMetas(constructor, getPackage(type)), instantiator,
                            findMigrationHandler(type), !context.isNoValidation()));
        }

        private String getPackage(final Class<?> type) {
            return ofNullable(type.getPackage()).map(Package::getName).orElse("");
        }

        private MigrationHandler findMigrationHandler(final Class<?> type) {
            return ofNullable(type.getAnnotation(Version.class)).map(Version::migrationHandler)
                                                                .filter(t -> t != MigrationHandler.class)
                                                                .flatMap(t -> Stream.of(t.getConstructors())
                                                                                    .sorted((o1, o2) -> o2.getParameterCount()
                                                                                            - o1.getParameterCount()).findFirst())
                                                                .map(t -> {
                                                                    try {
                                                                        return t.newInstance(
                                                                                reflections.parameterFactory(t, services)
                                                                                           .apply(emptyMap()));
                                                                    } catch (final InstantiationException | IllegalAccessException e) {
                                                                        throw new IllegalArgumentException(e);
                                                                    } catch (final InvocationTargetException e) {
                                                                        throw new IllegalArgumentException(
                                                                                e.getTargetException());
                                                                    }
                                                                }).map(MigrationHandler.class::cast).orElse(NO_MIGRATION);
        }

        private int findVersion(final Class<?> type) {
            return ofNullable(type.getAnnotation(Version.class)).map(Version::value).orElse(1);
        }

        private String findIcon(final AnnotatedElement type) {
            return ofNullable(type.getAnnotation(Icon.class))
                    .map(i -> i.value() == Icon.IconType.CUSTOM ? of(i.custom()).filter(s -> !s.isEmpty()).orElse("default")
                            : i.value().getKey())
                    .orElse("default");
        }

        private Constructor<?> findConstructor(final Class<?> type) {
            return Stream.of(type.getConstructors())
                         // we select the constructor with arguments which uses @Option or no-arg constructor if no param construct
                         // is matching
                         .sorted((c1, c2) -> {
                             final int options1 = Stream.of(c1.getParameters())
                                                        .mapToInt(p -> p.isAnnotationPresent(Option.class) ? 1 : 0).sum();
                             final int options2 = Stream.of(c1.getParameters())
                                                        .mapToInt(p -> p.isAnnotationPresent(Option.class) ? 1 : 0).sum();
                             if (options1 == options2) {
                                 final int paramCount1 = c1.getParameterCount();
                                 final int paramCount2 = c2.getParameterCount();
                                 return paramCount2 - paramCount1;
                             }
                             return options2 - options1;

                         }).findFirst()
                         .orElseThrow(() -> new IllegalArgumentException("No constructor usable in " + type.getName()));
        }

        // we keep the component (family) value since it is more user fiendly than having a generated id and it makes the
        // component
        // logical "id" more accurate
        private ComponentFamilyMeta getOrCreateComponent(final String component) {
            final String comp = ofNullable(component).filter(s -> !s.isEmpty()).orElseGet(components::family);
            if (comp.isEmpty()) {
                throw new IllegalArgumentException("Missing component");
            }
            return this.component == null || !component.equals(this.component.getName())
                    ? (this.component = new ComponentFamilyMeta(plugin, asList(components.categories()),
                    findIcon(familyAnnotationElement), comp,
                    Class.class.isInstance(familyAnnotationElement) ? getPackage(Class.class.cast(familyAnnotationElement)) : ""))
                    : this.component;
        }

        private Serializable doInvoke(final Constructor<?> constructor, Object[] args) {
            return executeInContainer(plugin, () -> {
                try {
                    return Serializable.class.cast(constructor.newInstance(args));
                } catch (final IllegalAccessException e) {
                    throw new IllegalStateException(e);
                } catch (final ClassCastException e) {
                    throw new IllegalArgumentException(constructor + " should return a Serializable", e);
                } catch (final InvocationTargetException e) {
                    throw new IllegalArgumentException(e.getTargetException());
                } catch (final InstantiationException e) {
                    throw new IllegalArgumentException(e);
                }
            });
        }
    }
}

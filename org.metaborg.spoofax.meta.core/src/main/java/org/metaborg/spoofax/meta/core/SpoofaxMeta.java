package org.metaborg.spoofax.meta.core;

import org.metaborg.core.MetaBorg;
import org.metaborg.core.MetaborgException;
import org.metaborg.core.plugin.IModulePluginLoader;
import org.metaborg.meta.core.MetaBorgMeta;
import org.metaborg.spoofax.core.Spoofax;
import org.metaborg.spoofax.meta.core.build.LanguageSpecBuilder;
import org.metaborg.spoofax.meta.core.build.TestRunner;
import org.metaborg.spoofax.meta.core.config.ISpoofaxLanguageSpecConfigBuilder;
import org.metaborg.spoofax.meta.core.config.ISpoofaxLanguageSpecConfigService;
import org.metaborg.spoofax.meta.core.project.ISpoofaxLanguageSpecService;

import com.google.inject.Module;

/**
 * Facade for instantiating and accessing the MetaBorg meta API, as an extension of the {@link MetaBorg} API,
 * instantiated with the Spoofax implementation.
 */
public class SpoofaxMeta extends MetaBorgMeta {
    @SuppressWarnings("hiding") public final Spoofax parent;

    public final LanguageSpecBuilder metaBuilder;
    public final TestRunner testRunner;

    @SuppressWarnings("hiding") public final ISpoofaxLanguageSpecService languageSpecService;
    @SuppressWarnings("hiding") public final ISpoofaxLanguageSpecConfigService languageSpecConfigService;


    /**
     * Instantiate the MetaBorg meta API, with a Spoofax implementation.
     * 
     * @param spoofax
     *            MetaBorg API, implemented by Spoofax, to extend.
     * @param loader
     *            Meta-module plugin loader to use.
     * @param module
     *            Spoofax meta-module to use.
     * @param additionalModules
     *            Additional modules to use.
     * 
     * @throws MetaborgException
     *             When loading plugins or dependency injection fails.
     */
    public SpoofaxMeta(Spoofax spoofax, IModulePluginLoader loader, SpoofaxMetaModule module,
        Module... additionalModules) throws MetaborgException {
        super(spoofax, loader, module, additionalModules);
        this.parent = spoofax;

        this.languageSpecService = injector.getInstance(ISpoofaxLanguageSpecService.class);
        this.languageSpecConfigService = injector.getInstance(ISpoofaxLanguageSpecConfigService.class);

        this.metaBuilder = injector.getInstance(LanguageSpecBuilder.class);
        this.testRunner = injector.getInstance(TestRunner.class);
    }

    /**
     * Instantiate the MetaBorg meta API, with a Spoofax implementation.
     * 
     * @param spoofax
     *            MetaBorg API, implemented by Spoofax, to extend.
     * @param module
     *            Spoofax meta-module to use.
     * @param additionalModules
     *            Additional modules to use.
     * 
     * @throws MetaborgException
     *             When loading plugins or dependency injection fails.
     */
    public SpoofaxMeta(Spoofax spoofax, SpoofaxMetaModule module, Module... additionalModules)
        throws MetaborgException {
        this(spoofax, defaultPluginLoader(), module, additionalModules);
    }

    /**
     * Instantiate the MetaBorg meta API, with a Spoofax implementation.
     * 
     * @param spoofax
     *            MetaBorg API, implemented by Spoofax, to extend.
     * @param loader
     *            Meta-module plugin loader to use.
     * @param additionalModules
     *            Additional modules to use.
     * 
     * @throws MetaborgException
     *             When loading plugins or dependency injection fails.
     */
    public SpoofaxMeta(Spoofax spoofax, IModulePluginLoader loader, Module... additionalModules)
        throws MetaborgException {
        this(spoofax, loader, defaultModule(), additionalModules);
    }

    /**
     * Instantiate the MetaBorg meta API, with a Spoofax implementation.
     * 
     * @param spoofax
     *            MetaBorg API, implemented by Spoofax, to extend.
     * @param additionalModules
     *            Additional modules to use.
     * 
     * @throws MetaborgException
     *             When loading plugins or dependency injection fails.
     */
    public SpoofaxMeta(Spoofax spoofax, Module... additionalModules) throws MetaborgException {
        this(spoofax, defaultPluginLoader(), defaultModule(), additionalModules);
    }


    /**
     * @return Fresh language specification configuration builder.
     */
    public ISpoofaxLanguageSpecConfigBuilder languageSpecConfigBuilder() {
        return injector.getInstance(ISpoofaxLanguageSpecConfigBuilder.class);
    }


    protected static SpoofaxMetaModule defaultModule() {
        return new SpoofaxMetaModule();
    }
}

package org.metaborg.spoofax.core.analysis.constraint;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.metaborg.core.MetaborgException;
import org.metaborg.core.analysis.AnalysisException;
import org.metaborg.core.messages.IMessage;
import org.metaborg.core.messages.MessageFactory;
import org.metaborg.core.resource.IResourceService;
import org.metaborg.meta.nabl2.constraints.IConstraint;
import org.metaborg.meta.nabl2.constraints.messages.IMessageInfo;
import org.metaborg.meta.nabl2.constraints.messages.ImmutableMessageInfo;
import org.metaborg.meta.nabl2.constraints.messages.MessageContent;
import org.metaborg.meta.nabl2.constraints.messages.MessageKind;
import org.metaborg.meta.nabl2.solver.Solution;
import org.metaborg.meta.nabl2.solver.Solver;
import org.metaborg.meta.nabl2.solver.SolverException;
import org.metaborg.meta.nabl2.solver.messages.Messages;
import org.metaborg.meta.nabl2.spoofax.analysis.Actions;
import org.metaborg.meta.nabl2.spoofax.analysis.CustomSolution;
import org.metaborg.meta.nabl2.spoofax.analysis.FinalResult;
import org.metaborg.meta.nabl2.spoofax.analysis.InitialResult;
import org.metaborg.meta.nabl2.spoofax.analysis.UnitResult;
import org.metaborg.meta.nabl2.terms.ITerm;
import org.metaborg.meta.nabl2.terms.ITermVar;
import org.metaborg.meta.nabl2.terms.generic.TB;
import org.metaborg.meta.nabl2.util.functions.Function1;
import org.metaborg.spoofax.core.analysis.AnalysisCommon;
import org.metaborg.spoofax.core.analysis.ISpoofaxAnalyzeResults;
import org.metaborg.spoofax.core.analysis.ISpoofaxAnalyzer;
import org.metaborg.spoofax.core.analysis.SpoofaxAnalyzeResults;
import org.metaborg.spoofax.core.context.scopegraph.ISingleFileScopeGraphContext;
import org.metaborg.spoofax.core.context.scopegraph.ISingleFileScopeGraphUnit;
import org.metaborg.spoofax.core.stratego.IStrategoCommon;
import org.metaborg.spoofax.core.stratego.IStrategoRuntimeService;
import org.metaborg.spoofax.core.terms.ITermFactoryService;
import org.metaborg.spoofax.core.tracing.ISpoofaxTracingService;
import org.metaborg.spoofax.core.unit.AnalyzeContrib;
import org.metaborg.spoofax.core.unit.ISpoofaxAnalyzeUnit;
import org.metaborg.spoofax.core.unit.ISpoofaxParseUnit;
import org.metaborg.spoofax.core.unit.ISpoofaxUnitService;
import org.metaborg.util.iterators.Iterables2;
import org.metaborg.util.log.ILogger;
import org.metaborg.util.log.Level;
import org.metaborg.util.log.LoggerUtils;
import org.metaborg.util.task.ICancel;
import org.metaborg.util.task.IProgress;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.strategoxt.HybridInterpreter;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

public class ConstraintSingleFileAnalyzer extends AbstractConstraintAnalyzer<ISingleFileScopeGraphContext>
        implements ISpoofaxAnalyzer {

    public static final ILogger logger = LoggerUtils.logger(ConstraintSingleFileAnalyzer.class);

    public static final String name = "constraint-singlefile";

    private final ISpoofaxUnitService unitService;

    @Inject public ConstraintSingleFileAnalyzer(final AnalysisCommon analysisCommon,
            final ISpoofaxUnitService unitService, final IResourceService resourceService,
            final IStrategoRuntimeService runtimeService, final IStrategoCommon strategoCommon,
            final ITermFactoryService termFactoryService, final ISpoofaxTracingService tracingService) {
        super(analysisCommon, resourceService, runtimeService, strategoCommon, termFactoryService, tracingService);
        this.unitService = unitService;
    }

    @Override protected ISpoofaxAnalyzeResults analyzeAll(Map<String, ISpoofaxParseUnit> changed, Set<String> removed,
            ISingleFileScopeGraphContext context, HybridInterpreter runtime, String strategy, IProgress progress,
            ICancel cancel) throws AnalysisException {
        final Level debugLevel = debugLevel(context);

        for(String input : removed) {
            context.removeUnit(input);
        }

        final int n = changed.size();
        progress.setWorkRemaining(n + 1);

        logger.log(debugLevel, "Analyzing {} files in {}.", n, context.location());
        final Collection<ISpoofaxAnalyzeUnit> results = Lists.newArrayList();
        try {
            for(Map.Entry<String, ISpoofaxParseUnit> input : changed.entrySet()) {
                final String source = input.getKey();
                final ISpoofaxParseUnit parseUnit = input.getValue();
                final ITerm ast = strategoTerms.fromStratego(parseUnit.ast());

                logger.log(debugLevel, "Analyzing {}.", source);
                final ISingleFileScopeGraphUnit unit = context.unit(source);
                unit.clear();

                try {
                    // initial
                    InitialResult initialResult;
                    final Optional<ITerm> customInitial;
                    {
                        logger.log(debugLevel, "Collecting initial constraints.");
                        ITerm initialResultTerm = doAction(strategy, Actions.analyzeInitial(source), context, runtime)
                                .orElseThrow(() -> new AnalysisException(context, "No initial result."));
                        initialResult = InitialResult.matcher().match(initialResultTerm)
                                .orElseThrow(() -> new MetaborgException("Invalid initial results."));
                        customInitial = doCustomAction(strategy, Actions.customInitial(source), context, runtime);
                        initialResult = initialResult.withCustomResult(customInitial);
                        logger.log(debugLevel, "Collected {} initial constraints.",
                                initialResult.getConstraints().size());
                    }

                    // unit
                    UnitResult unitResult;
                    final Optional<ITerm> customUnit;
                    {
                        logger.log(debugLevel, "Collecting file constraints.");
                        final ITerm unitResultTerm =
                                doAction(strategy, Actions.analyzeUnit(source, ast, initialResult.getArgs()), context,
                                        runtime).orElseThrow(() -> new AnalysisException(context, "No unit result."));
                        unitResult = UnitResult.matcher().match(unitResultTerm)
                                .orElseThrow(() -> new MetaborgException("Invalid unit results."));
                        final ITerm desugaredAST = unitResult.getAST();

                        customUnit = doCustomAction(strategy,
                                Actions.customUnit(source, desugaredAST, customInitial.orElse(TB.EMPTY_TUPLE)), context,
                                runtime);
                        unitResult = unitResult.withCustomResult(customUnit);
                        unit.setUnitResult(unitResult);
                        logger.log(debugLevel, "Collected {} file constraints.", unitResult.getConstraints().size());
                    }

                    // solve
                    final Solution solution;
                    {
                        logger.log(debugLevel, "Solving {} file constraints.");
                        Set<IConstraint> constraints =
                                Sets.union(initialResult.getConstraints(), unitResult.getConstraints());
                        Function1<String, ITermVar> fresh =
                                base -> TB.newVar(source, context.unit(source).fresh().fresh(base));
                        IMessageInfo messageInfo = ImmutableMessageInfo.of(MessageKind.ERROR, MessageContent.of(),
                                Actions.sourceTerm(source));
                        solution = Solver.solveFinal(initialResult.getConfig(), fresh, constraints,
                                Collections.emptySet(), messageInfo, progress.subProgress(1), cancel, debugLevel);
                        unit.setSolution(solution);
                        logger.log(debugLevel, "Solved file constraints.");
                    }

                    // final
                    FinalResult finalResult;
                    final Optional<ITerm> customFinal;
                    {
                        logger.log(debugLevel, "Finalizing file analysis.");
                        ITerm finalResultTerm = doAction(strategy, Actions.analyzeFinal(source), context, runtime)
                                .orElseThrow(() -> new AnalysisException(context, "No final result."));
                        finalResult = FinalResult.matcher().match(finalResultTerm)
                                .orElseThrow(() -> new MetaborgException("Invalid final results."));
                        customFinal =
                                doCustomAction(strategy,
                                        Actions.customFinal(source, customInitial.orElse(TB.EMPTY_TUPLE),
                                                customUnit.map(cu -> TB.newList(cu)).orElse(TB.EMPTY_LIST)),
                                        context, runtime);
                        finalResult = finalResult.withCustomResult(customFinal);
                        unit.setFinalResult(finalResult);
                        logger.log(debugLevel, "Finalized file analysis.");
                    }
                    final IStrategoTerm analyzedAST = strategoTerms.toStratego(unitResult.getAST());

                    Optional<CustomSolution> customSolution = customFinal.flatMap(CustomSolution.matcher()::match);
                    customSolution.ifPresent(cs -> unit.setCustomSolution(cs));

                    // errors
                    final boolean success;
                    {
                        logger.log(debugLevel, "Processing file messages.");
                        Messages messages = new Messages();
                        messages.addAll(Solver.unsolvedErrors(solution.getUnsolvedConstraints()));
                        messages.addAll(solution.getMessages());
                        customSolution.map(CustomSolution::getMessages).ifPresent(messages::addAll);

                        success = messages.getErrors().isEmpty();

                        Iterable<IMessage> fileMessages = Iterables.concat(
                                analysisCommon.ambiguityMessages(parseUnit.source(), analyzedAST),
                                messages(messages.getAll(), solution.getUnifier(), context, context.location()));

                        // result
                        results.add(unitService.analyzeUnit(parseUnit,
                                new AnalyzeContrib(true, success, true, analyzedAST, fileMessages, -1), context));

                        logger.info("Analyzed {}: {} errors, {} warnings, {} notes.", source,
                                messages.getErrors().size(), messages.getWarnings().size(), messages.getNotes().size());
                    }
                } catch(MetaborgException | SolverException e) {
                    logger.warn("Analysis of " + source + " failed.", e);
                    Iterable<IMessage> messages = Iterables2.singleton(
                            MessageFactory.newAnalysisErrorAtTop(parseUnit.source(), "File analysis failed.", e));
                    results.add(unitService.analyzeUnit(parseUnit,
                            new AnalyzeContrib(false, false, true, parseUnit.ast(), messages, -1), context));
                }
            }
        } catch(InterruptedException e) {
            logger.log(debugLevel, "Analysis was interrupted.");
        }

        logger.log(debugLevel, "Analyzed {} files.", n);
        return new SpoofaxAnalyzeResults(results, Collections.emptyList(), context);
    }

}
/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.processor;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TestRule;
import org.mockito.Mockito;
import org.slf4j.event.Level;

import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.Report.GlobalReportBuilderListener;
import net.sourceforge.pmd.Report.ProcessingError;
import net.sourceforge.pmd.Rule;
import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.RuleSet;
import net.sourceforge.pmd.RuleSets;
import net.sourceforge.pmd.internal.SystemProps;
import net.sourceforge.pmd.internal.util.ContextedAssertionError;
import net.sourceforge.pmd.lang.DummyLanguageModule;
import net.sourceforge.pmd.lang.Language;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.rule.AbstractRule;
import net.sourceforge.pmd.processor.MonoThreadProcessor.MonothreadRunnable;
import net.sourceforge.pmd.util.datasource.DataSource;
import net.sourceforge.pmd.util.log.MessageReporter;

public class PmdRunnableTest {

    @org.junit.Rule
    public TestRule restoreSystemProperties = new RestoreSystemProperties();



    private final DummyLanguageModule dummyLang = DummyLanguageModule.getInstance();

    private PMDConfiguration configuration;
    private PmdRunnable pmdRunnable;
    private GlobalReportBuilderListener reportBuilder;
    private MessageReporter reporter;
    private Rule rule;

    @Before
    public void prepare() {
        DataSource dataSource = DataSource.forString("test", "test.dummy");


        rule = spy(new RuleThatThrows());
        configuration = new PMDConfiguration();
        reportBuilder = new GlobalReportBuilderListener();
        reporter = mock(MessageReporter.class);
        configuration.setReporter(reporter);
        pmdRunnable = new MonothreadRunnable(new RuleSets(RuleSet.forSingleRule(rule)),
                dataSource,
                reportBuilder,
                configuration);

    }

    @Test
    public void inErrorRecoveryModeErrorsShouldBeLoggedByParser() {
        System.setProperty(SystemProps.PMD_ERROR_RECOVERY, "");
        configuration.setDefaultLanguageVersion(dummyLang.getVersionWithParserThatThrowsAssertionError());

        pmdRunnable.run();
        reportBuilder.close();
        Assert.assertEquals(1, reportBuilder.getResult().getProcessingErrors().size());
    }

    @Test
    public void inErrorRecoveryModeErrorsShouldBeLoggedByRule() {
        System.setProperty(SystemProps.PMD_ERROR_RECOVERY, "");
        configuration.setDefaultLanguageVersion(dummyLang.getDefaultVersion());

        pmdRunnable.run();
        reportBuilder.close();
        Report report = reportBuilder.getResult();
        List<ProcessingError> errors = report.getProcessingErrors();
        assertThat(errors, hasSize(1));
        assertThat(errors.get(0).getError(), instanceOf(ContextedAssertionError.class));
    }

    @Test
    public void withoutErrorRecoveryModeProcessingShouldBeAbortedByParser() {
        Assert.assertNull(System.getProperty(SystemProps.PMD_ERROR_RECOVERY));
        configuration.setDefaultLanguageVersion(dummyLang.getVersionWithParserThatThrowsAssertionError());

        Assert.assertThrows(AssertionError.class, pmdRunnable::run);
    }

    @Test
    public void withoutErrorRecoveryModeProcessingShouldBeAbortedByRule() {
        Assert.assertNull(System.getProperty(SystemProps.PMD_ERROR_RECOVERY));
        configuration.setDefaultLanguageVersion(dummyLang.getDefaultVersion());

        Assert.assertThrows(AssertionError.class, pmdRunnable::run);
    }


    @Test
    public void semanticErrorShouldAbortTheRun() {
        configuration.setDefaultLanguageVersion(dummyLang.getVersionWithParserThatReportsSemanticError());

        pmdRunnable.run();

        verify(reporter).log(eq(Level.INFO), contains("skipping rule analysis"));
        verify(rule, never()).apply(Mockito.any(), Mockito.any());
    }

    private static class RuleThatThrows extends AbstractRule {

        RuleThatThrows() {
            Language dummyLanguage = LanguageRegistry.findLanguageByTerseName(DummyLanguageModule.TERSE_NAME);
            setLanguage(dummyLanguage);
        }

        @Override
        public void apply(Node target, RuleContext ctx) {
            throw new AssertionError("test");
        }
    }
}

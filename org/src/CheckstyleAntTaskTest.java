package com.puppycrawl.tools.checkstyle.ant;

import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.resources.FileResource;
import org.junit.jupiter.api.Test;

import com.google.common.truth.StandardSubjectBuilder;
import com.puppycrawl.tools.checkstyle.AbstractPathTestSupport;
import com.puppycrawl.tools.checkstyle.DefaultLogger;
import com.puppycrawl.tools.checkstyle.Definitions;
import com.puppycrawl.tools.checkstyle.SarifLogger;
import com.puppycrawl.tools.checkstyle.XMLLogger;
import com.puppycrawl.tools.checkstyle.internal.testmodules.CheckstyleAntTaskLogStub;
import com.puppycrawl.tools.checkstyle.internal.testmodules.CheckstyleAntTaskStub;
import com.puppycrawl.tools.checkstyle.internal.testmodules.MessageLevelPair;
import com.puppycrawl.tools.checkstyle.internal.testmodules.TestRootModuleChecker;

public class CheckstyleAntTaskTest extends AbstractPathTestSupport {

    private static final String FLAWLESS_INPUT =
            "InputCheckstyleAntTaskFlawless.java";
    private static final String VIOLATED_INPUT =
            "InputCheckstyleAntTaskError.java";
    private static final String WARNING_INPUT =
            "InputCheckstyleAntTaskWarning.java";
    private static final String CONFIG_FILE =
            "InputCheckstyleAntTaskTestChecks.xml";
    private static final String CUSTOM_ROOT_CONFIG_FILE =
            "InputCheckstyleAntTaskConfigCustomRootModule.xml";
    private static final String NOT_EXISTING_FILE = "target/not_existing.xml";
    private static final String FAILURE_PROPERTY_VALUE = "myValue";

    @Override
    protected String getPackageLocation() {
        return "com/puppycrawl/tools/checkstyle/ant/checkstyleanttask/";
    }

    private CheckstyleAntTask getCheckstyleAntTask() throws IOException {
        return getCheckstyleAntTask(CONFIG_FILE);
    }

    private CheckstyleAntTask getCheckstyleAntTask(String configFile) throws IOException {
        final CheckstyleAntTask antTask = new CheckstyleAntTask();
        antTask.setConfig(getPath(configFile));
        antTask.setProject(new Project());
        return antTask;
    }

    @Test
    public final void testNoConfigFile() throws IOException {
        assertThrowsBuildExceptionWithoutConfig();
    }

    private void assertThrowsBuildExceptionWithoutConfig() throws IOException {
        final CheckstyleAntTask antTask = new CheckstyleAntTask();
        antTask.setProject(new Project());
        antTask.setFile(new File(getPath(FLAWLESS_INPUT)));
        final BuildException ex = assertThrows(BuildException.class,
                antTask::execute,
                "BuildException is expected");
        assertWithMessage("Error message is unexpected")
                .that(ex.getMessage())
                .isEqualTo("Must specify 'config'.");
    }

    // ... other test methods ...
}
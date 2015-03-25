package es.litesolutions.sonar.dummylanguage;


import com.sonar.sslr.api.Grammar;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.FilePredicate;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputPath;
import org.sonar.api.batch.rule.CheckFactory;
import org.sonar.api.batch.rule.Checks;
import org.sonar.api.component.ResourcePerspectives;
import org.sonar.api.issue.Issuable;
import org.sonar.api.issue.Issue;
import org.sonar.api.resources.Project;
import org.sonar.api.rule.RuleKey;
import org.sonar.squidbridge.AstScanner;
import org.sonar.squidbridge.SquidAstVisitor;
import org.sonar.squidbridge.api.CheckMessage;
import org.sonar.squidbridge.api.SourceCode;
import org.sonar.squidbridge.api.SourceFile;
import org.sonar.squidbridge.indexer.QueryByType;

import java.io.File;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Squid sensor... Whatever that means!
 *
 * <p>This seems to be what will be called when performing the actual parsing.
 * </p>
 */
@SuppressWarnings("UnnecessaryFullyQualifiedName")
public final class DummyLanguageSquidSensor
    implements Sensor
{
    private static final QueryByType SOURCE_FILES
        = new QueryByType(SourceFile.class);

    private final Checks<SquidAstVisitor<Grammar>> checks;

    /*
     * Yes, unfortunately, Sonar has "taken over" a name used by the JDK here.
     */
    private final org.sonar.api.batch.fs.FileSystem fs;

    private final FilePredicate predicate;
    private final ResourcePerspectives perspectives;

    public DummyLanguageSquidSensor(final org.sonar.api.batch.fs.FileSystem fs,
        final CheckFactory checkFactory,
        final ResourcePerspectives perspectives)
    {
        this.fs = fs;
        this.perspectives = perspectives;

        predicate = fs.predicates().hasLanguage(DummyLanguageLanguage.KEY);

        // TODO: redundancy with ObjectScriptRuleRepository?
        checks = checkFactory.
            <SquidAstVisitor<Grammar>>create(DummyLanguageChecks.REPOSITORY_KEY)
            .addAnnotatedChecks(DummyLanguageChecks.all());
    }

    /*
     * This LOOKS like it is what will be effectively called by the Sonar
     * runner.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void analyse(final Project module, final SensorContext context)
    {
        final Collection<SquidAstVisitor<Grammar>> all = checks.all();
        final SquidAstVisitor[] visitors
            = all.toArray(new SquidAstVisitor[all.size()]);

        final DummyLanguageConfiguration cfg = new DummyLanguageConfiguration();
        final AstScanner<Grammar> scanner = DummyLanguageAstScanner.create(cfg,
                visitors);

        for (final File file: fs.files(predicate))
            scanner.scanFile(file);

        for (final SourceCode code: scanner.getIndex().search(SOURCE_FILES))
            save((SourceFile) code);
    }

    private void save(final SourceFile squidFile)
    {
        final File file = new File(squidFile.getKey());
        final FilePredicate byName = fs.predicates().is(file);
        final InputFile inputFile = fs.inputFile(byName);
        final Set<CheckMessage> messages = squidFile.getCheckMessages();

        recordIssues(inputFile, messages);
    }

    private void recordIssues(final InputPath inputFile,
        final Iterable<CheckMessage> messages)
    {
        SquidAstVisitor<Grammar> visitor;
        RuleKey ruleKey;
        Issuable issuable;
        Issue issue;

        for (final CheckMessage message: messages) {
            issuable = perspectives.as(Issuable.class, inputFile);

            // Uh?
            if (issuable == null)
                continue;

            //noinspection unchecked
            visitor = (SquidAstVisitor<Grammar>) message.getCheck();
            ruleKey = checks.ruleKey(visitor);

            issue = issuable.newIssueBuilder()
                .ruleKey(ruleKey)
                .line(message.getLine())
                .message(message.getText(Locale.ENGLISH))
                .build();

            issuable.addIssue(issue);
        }
    }

    @Override
    public boolean shouldExecuteOnProject(final Project project)
    {
        return fs.hasFiles(predicate);
    }
}
package ru.neoflex.emf.stringtemplate;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CodePointBuffer;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.eclipse.emf.ecore.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import ru.neoflex.emf.hron.HbHronSupport;
import ru.neoflex.emf.hron.HronEvaluator;
import ru.neoflex.emf.hron.HronLexer;
import ru.neoflex.emf.hron.HronParser;
import ru.neoflex.emf.restserver.DBServerSvc;

import javax.annotation.PostConstruct;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@SpringBootTest(properties = {
        "db-name=hrontest"
}, classes = {ru.neoflex.emf.restserver.DBServerSvc.class})
public class HronTests {
    @Autowired
    private DBServerSvc dbServerSvc;

    @PostConstruct
    void init() {
        dbServerSvc.getDbServer().registerEPackage(StringtemplatePackage.eINSTANCE);
    }

    @Test
    public void loadMyClass() throws Exception {
        ClassPathResource myClassResource = new ClassPathResource("myClass.hron");
        String code = new String(Files.readAllBytes(myClassResource.getFile().toPath()), StandardCharsets.UTF_8);
        CharStream input = CodePointCharStream.fromBuffer(
                CodePointBuffer.withChars(CharBuffer.wrap(code.toCharArray())));
        HronLexer lexer = new HronLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        HronParser parser = new HronParser(tokens);
        ParseTree tree = parser.resource();
        Resource resource = dbServerSvc.getDbServer().createResource();
        HronEvaluator evaluator = new HronEvaluator(resource, new HbHronSupport(dbServerSvc.getDbServer()));
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(evaluator, tree);
        resource.save(null);
    }
}
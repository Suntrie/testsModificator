package com.repoMiner;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

public class TestsModificator {

    final private JavaParser javaParser;
    final private String baseDir;
    final private String testsDir;
    final private Set<Path> pathsToTestSourceFiles;
    final private File generatedTestDriver;
    final private CompilationUnit testDriverCompilationUnit = new CompilationUnit();
    final private Set<ClassOrInterfaceDeclaration> abstractDataTypes = new HashSet<>();

    private static Set<Class> annotationClasses = new HashSet<Class>() {{
        add(org.junit.Test.class);
        add(org.junit.BeforeClass.class);
        add(org.junit.AfterClass.class);
        add(org.junit.Before.class);
        add(org.junit.After.class);
        add(org.junit.Ignore.class);
    }};

    private Set<Path> fileSearch(String name, Path dir) {

        Set<Path> paths = new HashSet<>();

        File wantedDir = dir.toFile();
        File[] list = wantedDir.listFiles();
        if (list == null) return new HashSet<>();

        for (File f : list) {
            if (f.isFile() && f.getName().matches(name)) {
                paths.add(Paths.get(f.getAbsolutePath()));
            }
            if (f.isDirectory()) {
                Set<Path> newPaths = fileSearch(name, f.toPath());
                paths.addAll(newPaths);
            }
        }
        return paths;
    }

    public TestsModificator(String baseDir, String testsDir)
            throws IOException, XmlPullParserException {

        this.baseDir = baseDir;
        this.testsDir = testsDir;

        modifyDependencyVersion(baseDir);

        Long timestamp = System.currentTimeMillis();

        this.javaParser = new JavaParser();

        this.pathsToTestSourceFiles = fileSearch("(?i:(.*)test(.*)).java", Paths.get(testsDir));

        String testDriverName = "GeneratedTestDriver_" + timestamp;
        String packageName = "generatedTests_" + timestamp;

        MethodDeclaration testDriverMethodDeclaration = prepareTestDriverCompilationUnit(testDriverName, packageName);


        //TODO: base imports for junit3

        for (Path path : this.pathsToTestSourceFiles) {
            ParseResult result = javaParser.parse(new FileInputStream(String.valueOf(path)));
            if (!result.isSuccessful()) {
                System.out.println(result.getProblems());
            } else {
                CompilationUnit compilationUnit = (CompilationUnit) result.getResult().get();

                if (compilationUnit == null) {
                    System.out.println("Unsuccessful parsing, test:" + path.toString());
                } else {

                    AnnotationVisitor annotationVisitor = new AnnotationVisitor(compilationUnit, path);

                    compilationUnit.accept(annotationVisitor, null);

                    if (annotationVisitor.unsupportedJunitAnnotationFound) {
                        System.out.println("Unsuccessful parsing, test:" + path.toString()); // не должны такое обрабатывать
                    } else {

                        for (Node node: annotationVisitor.nodesToRemove){
                            node.remove();
                        }

                        if (annotationVisitor.beforeEachMethods.size()!=0) {
                            mergeFrameMethods(annotationVisitor.beforeEachMethods, "setUp");
                        }

                        if (annotationVisitor.afterEachMethods.size()!=0){
                            mergeFrameMethods(annotationVisitor.afterEachMethods, "tearDown");
                        }

                        java.nio.file.Files.write(path,
                                Collections.singleton(compilationUnit.toString()),
                                StandardCharsets.UTF_8, new StandardOpenOption[]{CREATE,TRUNCATE_EXISTING});

                        addMethodsCallsToDriverClass(annotationVisitor, testDriverMethodDeclaration);
                    }
                }

            }
        }

        // TODO: check if it's empty

        Path packagePath = Paths.get(testsDir + "//" + packageName);

        Files.createDirectories(packagePath);

        this.generatedTestDriver = new File(packagePath+"//"+testDriverName+".java");

        java.nio.file.Files.write(Paths.get(generatedTestDriver.getPath()),
                Collections.singleton(testDriverCompilationUnit.toString()),
                StandardCharsets.UTF_8, new StandardOpenOption[]{CREATE});
    }

    private void modifyDependencyVersion(String baseDir) throws IOException, XmlPullParserException {
        MavenXpp3Reader reader = new MavenXpp3Reader();

        File pomFile = new File(this.baseDir, "/pom.xml");
        Model model = reader.read(new FileInputStream(pomFile));

        for (Dependency dependency: model.getDependencies()){
            if (dependency.getGroupId().contains("junit") &&
                    dependency.getArtifactId().contains("junit")){
                dependency.setVersion("3.8.2");
            }
        }
        MavenXpp3Writer writer = new MavenXpp3Writer();
        writer.write(new FileOutputStream(pomFile), model);
    }

    private void mergeFrameMethods(Set<MethodDeclaration> methods, String methodName) {
        ClassOrInterfaceDeclaration classOrInterfaceDeclaration = getClassOrInterfaceDeclaration(
                methods.iterator().next());
        MethodDeclaration addedMethodDeclaration =
                classOrInterfaceDeclaration.addMethod(methodName, Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC);

        for (MethodDeclaration methodDeclaration : methods) {

            NodeList<Statement> statements = methodDeclaration.getBody().get().getStatements();

            for (Statement statement : statements) {
                addedMethodDeclaration.setBody(addedMethodDeclaration.getBody().get().
                        addStatement(statement));
            }
        }

        Iterator<MethodDeclaration> methodDeclarationIterator = methods.iterator();
        while (methodDeclarationIterator.hasNext()){

            MethodDeclaration methodDeclaration = methodDeclarationIterator.next();

            methodDeclarationIterator.remove();
            methodDeclaration.remove();
        }

        //methods.add(addedMethodDeclaration);
    }

    private MethodDeclaration prepareTestDriverCompilationUnit(String testDriverName, String packageName) {
        testDriverCompilationUnit.setPackageDeclaration(packageName);

        ClassOrInterfaceDeclaration generatedClass = testDriverCompilationUnit.addClass(testDriverName);
        MethodDeclaration methodDeclaration =
                generatedClass.addMethod("driveTests",
                        Modifier.Keyword.PUBLIC,
                        Modifier.Keyword.STATIC);
        methodDeclaration.addThrownException(Exception.class);

        return methodDeclaration;
    }

    private void addMethodsCallsToDriverClass(AnnotationVisitor annotationVisitor, MethodDeclaration testDriverMethodDeclaration) {

        addImportStatement(annotationVisitor);

        addMethodCalls(annotationVisitor.beforeAllMethods, testDriverMethodDeclaration, null);
        addObjectMethodCalls(annotationVisitor.testMethods,testDriverMethodDeclaration, annotationVisitor.beforeEachMethods,
                annotationVisitor.afterEachMethods);
        addMethodCalls(annotationVisitor.afterAllMethods, testDriverMethodDeclaration, null);


    }

    private void addImportStatement(AnnotationVisitor annotationVisitor) {

        for (ClassOrInterfaceDeclaration classOrInterfaceDeclaration: annotationVisitor.classesToImport){
            testDriverCompilationUnit.addImport(classOrInterfaceDeclaration.getFullyQualifiedName().get());
        }

    }

    private void addMethodCalls(Set<MethodDeclaration> methodDeclarations, MethodDeclaration testDriverMethodDeclaration,
                                VariableDeclarationExpr variableDeclarationExpr) {
        for (MethodDeclaration methodDeclaration : methodDeclarations) {

            MethodCallExpr call = variableDeclarationExpr == null? new MethodCallExpr(
                    ((ClassOrInterfaceDeclaration)methodDeclaration.getParentNode().get()).getFullyQualifiedName().get()+"."+
                    methodDeclaration.getNameAsString()):
                    new MethodCallExpr(variableDeclarationExpr.getVariables().iterator().next().getNameAsExpression(),
                            methodDeclaration.getNameAsString());

            testDriverMethodDeclaration.setBody(
                    testDriverMethodDeclaration.getBody().get().
                            addStatement(new ExpressionStmt(call)));
        }
    }


    private void addObjectMethodCalls(Set<MethodDeclaration> methodDeclarations, MethodDeclaration testDriverMethodDeclaration,
                                      Set<MethodDeclaration> beforeEachMethods, Set<MethodDeclaration> afterEachMethods) {
        for (MethodDeclaration methodDeclaration : methodDeclarations) {

            ClassOrInterfaceType classOrInterfaceType = getClassOrInterfaceType(methodDeclaration);

            ObjectCreationExpr objectCreationExpr = new ObjectCreationExpr(null, classOrInterfaceType,null);

            VariableDeclarationExpr variableDeclarationExpr = new VariableDeclarationExpr(new VariableDeclarator(classOrInterfaceType, "testObj_"+ System.currentTimeMillis(),
                    objectCreationExpr));

            testDriverMethodDeclaration.setBody(
                    testDriverMethodDeclaration.getBody().get().
                            addStatement(new ExpressionStmt(variableDeclarationExpr)));

            addMethodCalls(beforeEachMethods, testDriverMethodDeclaration, variableDeclarationExpr);

            MethodCallExpr call = new MethodCallExpr(variableDeclarationExpr.getVariable(0).getNameAsExpression(), methodDeclaration.getNameAsString());

            testDriverMethodDeclaration.setBody(
                    testDriverMethodDeclaration.getBody().get().
                            addStatement(new ExpressionStmt(call)));


            addMethodCalls(afterEachMethods, testDriverMethodDeclaration, variableDeclarationExpr);

        }
    }

    private ClassOrInterfaceType getClassOrInterfaceType(MethodDeclaration methodDeclaration) {
        ClassOrInterfaceDeclaration classOrInterfaceDeclaration = getClassOrInterfaceDeclaration(methodDeclaration);

        String classOrInterfaceDeclarationName = classOrInterfaceDeclaration.getFullyQualifiedName().get();

        return javaParser.parseClassOrInterfaceType(classOrInterfaceDeclarationName).getResult().get();
    }

    private ClassOrInterfaceDeclaration getClassOrInterfaceDeclaration(MethodDeclaration methodDeclaration) {
        return (ClassOrInterfaceDeclaration)(methodDeclaration
                    .getParentNode().get());
    }

    // Just now there can't exist nested items
    private class AnnotationVisitor extends VoidVisitorAdapter<Void> {

        private CompilationUnit cu;
        private ClassOrInterfaceType classOrInterfaceType;
        private Path path;
        private Boolean unsupportedJunitAnnotationFound= false;
        //private Boolean isAbstractDataType = false;

        private Set<MethodDeclaration> testMethods = new HashSet<>();
        private Set<MethodDeclaration> beforeAllMethods = new HashSet<>();
        private Set<MethodDeclaration> afterAllMethods = new HashSet<>();
        private Set<MethodDeclaration> beforeEachMethods = new HashSet<>();
        private Set<MethodDeclaration> afterEachMethods = new HashSet<>();

        private List<Node> nodesToRemove = new ArrayList<>();
        private List<ClassOrInterfaceDeclaration> classesToImport = new ArrayList<>();


        public AnnotationVisitor(CompilationUnit cu, Path path) {
            this.cu = cu;
            this.path = path;
        }
/*
        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            super.visit(n, arg);
            if (n.isAbstract()) {
                isAbstractDataType = true;
            }
        }*/

        public Boolean containsAnnotations() {
            return testMethods.size() != 0;
        }

        MethodDeclaration beforeMethodDeclaration;
        MethodDeclaration afterMethodDeclaration;


        private void processAnnotation(AnnotationExpr n) {
            if (n.getParentNode().isPresent()) {
                Node it = n.getParentNode().get();

                String annotationName = n.getName().toString();

                MethodDeclaration methodDeclaration = ((MethodDeclaration) it);

                if (annotationName.matches("Test")) {
                   // nodesToRemove.add(methodDeclaration);
                    methodDeclaration.setName("test".concat(methodDeclaration.getNameAsString()));
                    testMethods.add(methodDeclaration);
                } else if (annotationName.matches("BeforeClass")) {
                    beforeAllMethods.add(methodDeclaration);
                } else if (annotationName.matches("AfterClass")) {
                    afterAllMethods.add(methodDeclaration);
                } else if (annotationName.matches("Before")) {
                    //methodDeclaration.setName("setUp");
                    beforeEachMethods.add(methodDeclaration);
                } else if (annotationName.matches("After")) {
                    //methodDeclaration.setName("tearDown");
                    afterEachMethods.add(methodDeclaration);
                }

                nodesToRemove.add(n);
            }else{
                System.out.println("Unavailable path.");
            }

        }

        @Override
        public void visit(MarkerAnnotationExpr n, Void arg) {
            super.visit(n, arg);

            processAnnotation(n);

        }

        @Override
        public void visit(NormalAnnotationExpr n, Void arg) {
            super.visit(n, arg);
            processAnnotation(n);
        }

        @Override
        public void visit(SingleMemberAnnotationExpr n, Void arg) {
            super.visit(n, arg);
            n.remove();
            processAnnotation(n);
        }


        public Boolean getUnsupportedJunitAnnotationFound() {
            return unsupportedJunitAnnotationFound;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            super.visit(n, arg);
            classesToImport.add(n);
        }
    }
}
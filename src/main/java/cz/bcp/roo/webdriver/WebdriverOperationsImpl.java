package cz.bcp.roo.webdriver;

import static org.springframework.roo.model.JavaType.LONG_OBJECT;
import static org.springframework.roo.model.JdkJavaType.BIG_DECIMAL;
import static org.springframework.roo.model.Jsr303JavaType.FUTURE;
import static org.springframework.roo.model.Jsr303JavaType.MIN;
import static org.springframework.roo.model.Jsr303JavaType.PAST;
import static org.springframework.roo.model.SpringJavaType.DATE_TIME_FORMAT;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.Validate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.springframework.roo.addon.dod.DataOnDemandOperations;
import org.springframework.roo.addon.web.mvc.controller.details.WebMetadataService;
import org.springframework.roo.addon.web.mvc.controller.scaffold.WebScaffoldMetadata;
import org.springframework.roo.classpath.PhysicalTypeCategory;
import org.springframework.roo.classpath.PhysicalTypeIdentifier;
import org.springframework.roo.classpath.TypeLocationService;
import org.springframework.roo.classpath.TypeManagementService;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetails;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetailsBuilder;
import org.springframework.roo.classpath.details.FieldMetadata;
import org.springframework.roo.classpath.details.FieldMetadataBuilder;
import org.springframework.roo.classpath.details.ImportMetadata;
import org.springframework.roo.classpath.details.ImportMetadataBuilder;
import org.springframework.roo.classpath.details.MemberFindingUtils;
import org.springframework.roo.classpath.details.MethodMetadataBuilder;
import org.springframework.roo.classpath.details.annotations.AnnotationAttributeValue;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadata;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadataBuilder;
import org.springframework.roo.classpath.itd.InvocableMemberBodyBuilder;
import org.springframework.roo.classpath.operations.DateTime;
import org.springframework.roo.classpath.scanner.MemberDetails;
import org.springframework.roo.classpath.scanner.MemberDetailsScanner;
import org.springframework.roo.metadata.MetadataService;
import org.springframework.roo.model.JavaPackage;
import org.springframework.roo.model.JavaSymbolName;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.project.Dependency;
import org.springframework.roo.project.LogicalPath;
import org.springframework.roo.project.Path;
import org.springframework.roo.project.ProjectOperations;
import org.springframework.roo.support.logging.HandlerUtils;
import org.springframework.roo.support.util.XmlUtils;
import org.w3c.dom.Element;

/**
 * Implementation of operations this add-on offers.
 * 
 * @since 1.1
 */
@Component
// Use these Apache Felix annotations to register your commands class in the Roo
// container
@Service
public class WebdriverOperationsImpl implements WebdriverOperations {
	private static final Logger LOGGER = HandlerUtils
            .getLogger(WebdriverOperationsImpl.class);

	private static final JavaType TEST = new JavaType("org.junit.Test");
	private static final JavaType BEFORE = new JavaType("org.junit.Before");
	private static final JavaType AFTER = new JavaType("org.junit.After");
	private static final JavaType WEBDRIVER = new JavaType("org.openqa.selenium.WebDriver");
	
	/**
	 * Use ProjectOperations to install new dependencies, plugins, properties,
	 * etc into the project configuration
	 */
	@Reference private ProjectOperations projectOperations;	

	/**
	 * Use TypeLocationService to find types which are annotated with a given
	 * annotation in the project
	 */
	@Reference private TypeLocationService typeLocationService;

	/**
	 * Use TypeManagementService to change types
	 */
	@Reference private TypeManagementService typeManagementService;
	
	@Reference private DataOnDemandOperations dataOnDemandOperations;
	@Reference private MetadataService metadataService;
	@Reference private MemberDetailsScanner memberDetailsScanner;
	@Reference private WebMetadataService webMetadataService;

	/** {@inheritDoc} */
	public boolean isCommandAvailable() {
		// Check if a project has been created
		return projectOperations.isFocusedProjectAvailable();
	}

	/** {@inheritDoc} */
	public void createTestClass(JavaType controller) {

		Validate.notNull(controller, "Controller type required");

        final ClassOrInterfaceTypeDetails controllerTypeDetails = typeLocationService
                .getTypeDetails(controller);
        Validate.notNull(
                controllerTypeDetails,
                "Class or interface type details for type '%s' could not be resolved",
                controller);

        final LogicalPath path = PhysicalTypeIdentifier
                .getPath(controllerTypeDetails.getDeclaredByMetadataId());
        final String webScaffoldMetadataIdentifier = WebScaffoldMetadata
                .createIdentifier(controller, path);
        final WebScaffoldMetadata webScaffoldMetadata = (WebScaffoldMetadata) metadataService
                .get(webScaffoldMetadataIdentifier);
        Validate.notNull(
                webScaffoldMetadata,
                "Web controller '%s' does not appear to be an automatic, scaffolded controller",
                controller.getFullyQualifiedTypeName());

        // abort the creation of a selenium test if the controller does not
        // allow the creation of new instances for the form backing object
        if (!webScaffoldMetadata.getAnnotationValues().isCreate()) {
            LOGGER.warning("The controller you specified does not allow the creation of new instances of the form backing object. No Selenium tests created.");
            return;
        }
        
		final JavaType name = new JavaType(controller + "SeleniumTest");
		final String declaredByMetadataId = PhysicalTypeIdentifier
				.createIdentifier(name,
						Path.SRC_TEST_JAVA.getModulePathId(path.getModule()));

		if (metadataService.get(declaredByMetadataId) != null) {
			LOGGER.log(Level.SEVERE, "The file already exists");
			return;
		}		
		
		// Create imports		
		String firefoxDriver = "org.openqa.selenium.firefox.FirefoxDriver";
		ImportMetadataBuilder firefoxImportBuilder = new ImportMetadataBuilder(declaredByMetadataId, 0, new JavaPackage(firefoxDriver), new JavaType(firefoxDriver), false, false);				
		String timeUnit = "java.util.concurrent.TimeUnit";
		ImportMetadataBuilder timeUnitImportBuilder = new ImportMetadataBuilder(declaredByMetadataId, 0, new JavaPackage(timeUnit), new JavaType(timeUnit), false, false);		
		String assertPackage = "org.junit.Assert";
		ImportMetadataBuilder assertImportBuilder = new ImportMetadataBuilder(declaredByMetadataId, 0, new JavaPackage(assertPackage), new JavaType(assertPackage), false, false);
		String byPackage = "org.openqa.selenium.By";
		ImportMetadataBuilder byImportBuilder = new ImportMetadataBuilder(declaredByMetadataId, 0, new JavaPackage(byPackage), new JavaType(byPackage), false, false);
		String webElementPackage = "org.openqa.selenium.WebElement";
		ImportMetadataBuilder webElementImportBuilder = new ImportMetadataBuilder(declaredByMetadataId, 0, new JavaPackage(webElementPackage), new JavaType(webElementPackage), false, false);
		
		final List<ImportMetadata> imports = new ArrayList<ImportMetadata>();
		imports.add(firefoxImportBuilder.build());
		imports.add(timeUnitImportBuilder.build());
		imports.add(assertImportBuilder.build());
		imports.add(byImportBuilder.build());
		imports.add(webElementImportBuilder.build());
				
		// Create fields
		final List<FieldMetadataBuilder> fields = new ArrayList<FieldMetadataBuilder>();
		fields.add(getDriverField(declaredByMetadataId));

		// Create methods
		final List<MethodMetadataBuilder> methods = new ArrayList<MethodMetadataBuilder>();
		methods.add(getBeforeMethod(declaredByMetadataId, controller));
		methods.add(getTestMethod(declaredByMetadataId, controller));
		methods.add(getAfterMethod(declaredByMetadataId, controller));

		// Create class
		final ClassOrInterfaceTypeDetailsBuilder cidBuilder = new ClassOrInterfaceTypeDetailsBuilder(
				declaredByMetadataId, Modifier.PUBLIC, name,
				PhysicalTypeCategory.CLASS);		
		cidBuilder.add(imports);
		cidBuilder.setDeclaredMethods(methods);
		cidBuilder.setDeclaredFields(fields);
		
		typeManagementService.createOrUpdateTypeOnDisk(cidBuilder.build());
	}

	/** {@inheritDoc} */
	public void addDependencies() {
		List<Dependency> dependencies = new ArrayList<Dependency>();

		// Install dependencies defined in external XML file
		for (Element dependencyElement : XmlUtils.findElements(
				"/configuration/batch/dependencies/dependency",
				XmlUtils.getConfiguration(getClass()))) {
			dependencies.add(new Dependency(dependencyElement));
		}

		// Add all new dependencies to pom.xml
		projectOperations.addDependencies("", dependencies);
	}
	
	private MethodMetadataBuilder getBeforeMethod(String declaredByMetadataId, JavaType controller) {
		final InvocableMemberBodyBuilder bodyBuilderBefore = new InvocableMemberBodyBuilder();		
		final List<AnnotationMetadataBuilder> methodAnnotationsBefore = new ArrayList<AnnotationMetadataBuilder>();
		methodAnnotationsBefore.add(new AnnotationMetadataBuilder(BEFORE));
		final MethodMetadataBuilder methodBuilderBefore = new MethodMetadataBuilder(
				declaredByMetadataId, Modifier.PUBLIC, new JavaSymbolName(
						"setUp"), JavaType.VOID_PRIMITIVE, bodyBuilderBefore);
		methodBuilderBefore.setAnnotations(methodAnnotationsBefore);
		return methodBuilderBefore;
	}	
	
	private MethodMetadataBuilder getTestMethod(String declaredByMetadataId, JavaType controller) {
		final InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		final ClassOrInterfaceTypeDetails controllerDetails = typeLocationService.getTypeDetails(controller);
		
		final ClassOrInterfaceTypeDetails controllerTypeDetails = typeLocationService
	                .getTypeDetails(controller);
		final LogicalPath path = PhysicalTypeIdentifier
                .getPath(controllerTypeDetails.getDeclaredByMetadataId());
        final String webScaffoldMetadataIdentifier = WebScaffoldMetadata
                .createIdentifier(controller, path);
		final WebScaffoldMetadata webScaffoldMetadata = (WebScaffoldMetadata) metadataService
                .get(webScaffoldMetadataIdentifier);
		final JavaType formBackingType = webScaffoldMetadata.getAnnotationValues().getFormBackingObject();
		
		 final ClassOrInterfaceTypeDetails formBackingTypeDetails = typeLocationService
	                .getTypeDetails(formBackingType);
	        Validate.notNull(
	                formBackingType,
	                "Class or interface type details for type '%s' could not be resolved",
	                formBackingType);
		final MemberDetails memberDetails = memberDetailsScanner
                .getMemberDetails(getClass().getName(), formBackingTypeDetails);
	
		final List<FieldMetadata> fields = webMetadataService
                .getScaffoldEligibleFieldMetadata(formBackingType,
                        memberDetails, null);

		String indexPageUrl = projectOperations.getProjectName(
								projectOperations.getFocusedModuleName()) 
								+ "/"
								+ webScaffoldMetadata.getAnnotationValues().getPath()
								+ "?form";
		if (controllerDetails != null) {		
				bodyBuilder.appendFormalLine("// open index page");
				bodyBuilder.appendFormalLine("driver.navigate().to(\"http://localhost:8080/" + indexPageUrl + "\");");
				bodyBuilder.append("\n");
				
				for (final FieldMetadata field : fields) {
		            final JavaType fieldType = field.getFieldType();
		            if (!fieldType.isCommonCollectionType()
		                    && !isSpecialType(fieldType)) {
		            	String fieldId = "_" + field.getFieldName().getSymbolName() + "_id";
		            	String testValue = convertToInitializer(field);
		            	String tesTextVariableName = field.getFieldName() + "String";
		            	bodyBuilder.appendFormalLine("// type " + fieldId + " " + testValue);
		            	bodyBuilder.appendFormalLine("WebElement "+ field.getFieldName() + "TextBox = driver.findElement(By.id(\"" + fieldId + "\"));");		
		            	bodyBuilder.appendFormalLine("String " + tesTextVariableName + " = \"" + testValue + "\";");
		            	bodyBuilder.appendFormalLine(field.getFieldName() + "TextBox.sendKeys(" + tesTextVariableName + ");");
		            	bodyBuilder.append("\n");
		            }
		        }
				
				bodyBuilder.appendFormalLine("// save new record and wait");
				bodyBuilder.appendFormalLine("driver.findElement(By.id(\"proceed\")).click();");	
				bodyBuilder.appendFormalLine("long end = System.currentTimeMillis() + 5000;");
				bodyBuilder.append("\n");	
				
				bodyBuilder.appendFormalLine("// verify text");				
            	bodyBuilder.appendFormalLine("while (System.currentTimeMillis() < end) {");
				for (int i = 0; i < fields.size(); i++) {
					FieldMetadata field = fields.get(i);				
		            final JavaType fieldType = field.getFieldType();
		            if (!fieldType.isCommonCollectionType()
		                    && !isSpecialType(fieldType)) {
		            	
		            	String tesTextVariableName = field.getFieldName() + "String";
		            	String resultDivId = "_s_"
		            						+ formBackingType.getFullyQualifiedTypeName() + "_"
		            						+ field.getFieldName().getSymbolName() + "_"
		            						+ field.getFieldName().getSymbolName() + "_id";
		            	resultDivId = resultDivId.replace('.', '_');		            	
		            	bodyBuilder.appendFormalLine("WebElement " + field.getFieldName() + "ResultsDiv = driver.findElement(By.id(\"" + resultDivId + "\"));");
		            	bodyBuilder.appendFormalLine("if (" + field.getFieldName() + "ResultsDiv.isDisplayed()) {");
		            	bodyBuilder.appendFormalLine("String text = " + field.getFieldName() + "ResultsDiv.getText();");
		            	bodyBuilder.appendFormalLine("Assert.assertEquals(" + tesTextVariableName + ", text);");
		            	if (i == fields.size()-1) {
		            		bodyBuilder.appendFormalLine("break;"); // za posledni iteraci
		            		bodyBuilder.appendFormalLine("}");
		            	}
		            	bodyBuilder.appendFormalLine("}");
		            	bodyBuilder.append("\n");
		            }		            
				}
			}
		
		final List<AnnotationMetadataBuilder> methodAnnotationTest = new ArrayList<AnnotationMetadataBuilder>();
		methodAnnotationTest.add(new AnnotationMetadataBuilder(TEST));
		final MethodMetadataBuilder methodBuilderTest = new MethodMetadataBuilder(
				declaredByMetadataId, Modifier.PUBLIC, new JavaSymbolName(
						"test"), JavaType.VOID_PRIMITIVE,
						bodyBuilder);
		methodBuilderTest.setAnnotations(methodAnnotationTest);
		return methodBuilderTest;
	}
		
		private boolean isSpecialType(final JavaType javaType) {
	        return typeLocationService.isInProject(javaType);
	    }
	
	private MethodMetadataBuilder getAfterMethod(String declaredByMetadataId, JavaType controller) {
		final InvocableMemberBodyBuilder bodyBuilderAfter = new InvocableMemberBodyBuilder();
		final ClassOrInterfaceTypeDetails controllerDetails = typeLocationService
				.getTypeDetails(controller);
		if (controllerDetails != null) {			
			bodyBuilderAfter.appendFormalLine("driver.quit();");		
		}
		final List<AnnotationMetadataBuilder> methodAnnotationsAfter = new ArrayList<AnnotationMetadataBuilder>();
		methodAnnotationsAfter.add(new AnnotationMetadataBuilder(AFTER));
		final MethodMetadataBuilder methodBuilderAfter = new MethodMetadataBuilder(
				declaredByMetadataId, Modifier.PUBLIC, new JavaSymbolName(
						"tearDown"), JavaType.VOID_PRIMITIVE, bodyBuilderAfter);
		methodBuilderAfter.setAnnotations(methodAnnotationsAfter);
		return methodBuilderAfter;
	}
	
    private FieldMetadataBuilder getDriverField(String declaredByMetadataId) {
    	final FieldMetadataBuilder fieldBuilder = new FieldMetadataBuilder(declaredByMetadataId, // Metadata ID provided by supertype
	            Modifier.PRIVATE, 
	            new ArrayList<AnnotationMetadataBuilder>(), // No annotations for this field
	            new JavaSymbolName("driver"), // Field name
	            WEBDRIVER); // Field type
        
        return fieldBuilder;
    }    
    
    private String convertToInitializer(final FieldMetadata field) {
        String initializer = " ";
        short index = 1;
        final AnnotationMetadata min = MemberFindingUtils.getAnnotationOfType(
                field.getAnnotations(), MIN);
        if (min != null) {
            final AnnotationAttributeValue<?> value = min
                    .getAttribute(new JavaSymbolName("value"));
            if (value != null) {
                index = new Short(value.getValue().toString());
            }
        }
        final JavaType fieldType = field.getFieldType();
        if (field.getFieldName().getSymbolName().contains("email")
                || field.getFieldName().getSymbolName().contains("Email")) {
            initializer = "some@email.com";
        }
        else if (fieldType.equals(JavaType.STRING)) {
            initializer = "some"
                    + field.getFieldName()
                            .getSymbolNameCapitalisedFirstLetter() + index;
        }
        else if (fieldType.equals(new JavaType(Date.class.getName()))
                || fieldType.equals(new JavaType(Calendar.class.getName()))) {
            final Calendar cal = Calendar.getInstance();
            AnnotationMetadata dateTimeFormat = null;
            String style = null;
            if ((dateTimeFormat = MemberFindingUtils.getAnnotationOfType(
                    field.getAnnotations(), DATE_TIME_FORMAT)) != null) {
                final AnnotationAttributeValue<?> value = dateTimeFormat
                        .getAttribute(new JavaSymbolName("style"));
                if (value != null) {
                    style = value.getValue().toString();
                }
            }
            if (MemberFindingUtils.getAnnotationOfType(field.getAnnotations(),
                    PAST) != null) {
                cal.add(Calendar.YEAR, -1);
                cal.add(Calendar.MONTH, -1);
                cal.add(Calendar.DAY_OF_MONTH, -1);
            }
            else if (MemberFindingUtils.getAnnotationOfType(
                    field.getAnnotations(), FUTURE) != null) {
                cal.add(Calendar.YEAR, 1);
                cal.add(Calendar.MONTH, 1);
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            if (style != null) {
                if (style.startsWith("-")) {
                    initializer = ((SimpleDateFormat) DateFormat
                            .getTimeInstance(
                                    DateTime.parseDateFormat(style.charAt(1)),
                                    Locale.getDefault())).format(cal.getTime());
                }
                else if (style.endsWith("-")) {
                    initializer = ((SimpleDateFormat) DateFormat
                            .getDateInstance(
                                    DateTime.parseDateFormat(style.charAt(0)),
                                    Locale.getDefault())).format(cal.getTime());
                }
                else {
                    initializer = ((SimpleDateFormat) DateFormat
                            .getDateTimeInstance(
                                    DateTime.parseDateFormat(style.charAt(0)),
                                    DateTime.parseDateFormat(style.charAt(1)),
                                    Locale.getDefault())).format(cal.getTime());
                }
            }
            else {
                initializer = ((SimpleDateFormat) DateFormat.getDateInstance(
                        DateFormat.SHORT, Locale.getDefault())).format(cal
                        .getTime());
            }

        }
        else if (fieldType.equals(JavaType.BOOLEAN_OBJECT)
                || fieldType.equals(JavaType.BOOLEAN_PRIMITIVE)) {
            initializer = Boolean.valueOf(false).toString();
        }
        else if (fieldType.equals(JavaType.INT_OBJECT)
                || fieldType.equals(JavaType.INT_PRIMITIVE)) {
            initializer = Integer.valueOf(index).toString();
        }
        else if (fieldType.equals(JavaType.DOUBLE_OBJECT)
                || fieldType.equals(JavaType.DOUBLE_PRIMITIVE)) {
            initializer = Double.toString(index);
        }
        else if (fieldType.equals(JavaType.FLOAT_OBJECT)
                || fieldType.equals(JavaType.FLOAT_PRIMITIVE)) {
            initializer = Float.toString(index);
        }
        else if (fieldType.equals(LONG_OBJECT)
                || fieldType.equals(JavaType.LONG_PRIMITIVE)) {
            initializer = Long.valueOf(index).toString();
        }
        else if (fieldType.equals(JavaType.SHORT_OBJECT)
                || fieldType.equals(JavaType.SHORT_PRIMITIVE)) {
            initializer = Short.valueOf(index).toString();
        }
        else if (fieldType.equals(BIG_DECIMAL)) {
            initializer = new BigDecimal(index).toString();
        }
        return initializer;
    }

}
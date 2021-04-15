package org.esupportail.cas.config;

import org.apereo.cas.CentralAuthenticationService;
import org.apereo.cas.audit.AuditableExecution;
import org.apereo.cas.authentication.AuthenticationEventExecutionPlan;
import org.apereo.cas.authentication.AuthenticationServiceSelectionPlan;
import org.apereo.cas.authentication.AuthenticationSystemSupport;
import org.apereo.cas.authentication.MultifactorAuthenticationContextValidator;
import org.apereo.cas.authentication.MultifactorAuthenticationProviderSelector;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.ticket.TicketFactory;
import org.apereo.cas.ticket.registry.TicketRegistry;
import org.apereo.cas.ticket.registry.TicketRegistrySupport;
import org.apereo.cas.trusted.authentication.api.MultifactorAuthenticationTrustStorage;
import org.apereo.cas.web.cookie.CasCookieBuilder;
import org.apereo.cas.web.flow.CasWebflowConfigurer;
import org.apereo.cas.web.flow.CasWebflowExecutionPlan;
import org.apereo.cas.web.flow.CasWebflowExecutionPlanConfigurer;
import org.apereo.cas.web.flow.SingleSignOnParticipationStrategy;
import org.apereo.cas.web.flow.authentication.RankedMultifactorAuthenticationProviderSelector;
import org.apereo.cas.web.flow.resolver.CasDelegatingWebflowEventResolver;
import org.apereo.cas.web.flow.resolver.CasWebflowEventResolver;
import org.apereo.cas.web.flow.resolver.impl.CasWebflowEventResolutionConfigurationContext;
import org.apereo.cas.web.flow.util.MultifactorAuthenticationWebflowUtils;
import org.esupportail.cas.adaptors.esupotp.EsupOtpService;
import org.esupportail.cas.adaptors.esupotp.web.flow.EsupOtpAuthenticationWebflowAction;
import org.esupportail.cas.adaptors.esupotp.web.flow.EsupOtpAuthenticationWebflowEventResolver;
import org.esupportail.cas.adaptors.esupotp.web.flow.EsupOtpGetTransportsAction;
import org.esupportail.cas.adaptors.esupotp.web.flow.EsupOtpMultifactorTrustWebflowConfigurer;
import org.esupportail.cas.adaptors.esupotp.web.flow.EsupOtpMultifactorWebflowConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.webflow.config.FlowDefinitionRegistryBuilder;
import org.springframework.webflow.definition.registry.FlowDefinitionRegistry;
import org.springframework.webflow.engine.builder.support.FlowBuilderServices;
import org.springframework.webflow.execution.Action;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration("esupotpConfiguration")
@EnableConfigurationProperties(CasConfigurationProperties.class)
public class EsupOtpConfiguration {
		
	@Autowired
	private CasConfigurationProperties casProperties;

	@Autowired
	private ConfigurableApplicationContext applicationContext;
	
    @Autowired
    @Qualifier("defaultTicketFactory")
    private ObjectProvider<TicketFactory> ticketFactory;

    @Autowired
    @Qualifier("authenticationEventExecutionPlan")
    private ObjectProvider<AuthenticationEventExecutionPlan> authenticationEventExecutionPlan;

	@Autowired
	@Qualifier("loginFlowRegistry")
	private FlowDefinitionRegistry loginFlowDefinitionRegistry;

	@Autowired
	private FlowBuilderServices flowBuilderServices;

	@Autowired
	@Qualifier("builder")
	private FlowBuilderServices builder;

	@Autowired
	@Qualifier("centralAuthenticationService")
	private CentralAuthenticationService centralAuthenticationService;

	@Autowired
	@Qualifier("defaultAuthenticationSystemSupport")
	private AuthenticationSystemSupport authenticationSystemSupport;

	@Autowired
	@Qualifier("defaultTicketRegistrySupport")
	private TicketRegistrySupport ticketRegistrySupport;

	@Autowired
	@Qualifier("servicesManager")
	private ServicesManager servicesManager;

    @Autowired
    @Qualifier("singleSignOnParticipationStrategy")
    private ObjectProvider<SingleSignOnParticipationStrategy> webflowSingleSignOnParticipationStrategy;

    @Autowired
    @Qualifier("registeredServiceAccessStrategyEnforcer")
    private ObjectProvider<AuditableExecution> registeredServiceAccessStrategyEnforcer;


    @Autowired
    @Qualifier("authenticationServiceSelectionPlan")
    private ObjectProvider<AuthenticationServiceSelectionPlan> authenticationRequestServiceSelectionStrategies;

    @Autowired
    @Qualifier("initialAuthenticationAttemptWebflowEventResolver")
    private ObjectProvider<CasDelegatingWebflowEventResolver> initialAuthenticationAttemptWebflowEventResolver;

    @Autowired
    @Qualifier("authenticationContextValidator")
    private ObjectProvider<MultifactorAuthenticationContextValidator> authenticationContextValidator;

    @Autowired
    @Qualifier("warnCookieGenerator")
    private ObjectProvider<CasCookieBuilder> warnCookieGenerator;

    @Autowired
    @Qualifier("ticketRegistry")
    private ObjectProvider<TicketRegistry> ticketRegistry;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Autowired(required = false)
    @Qualifier("multifactorAuthenticationProviderSelector")
    private MultifactorAuthenticationProviderSelector multifactorAuthenticationProviderSelector =
            new RankedMultifactorAuthenticationProviderSelector();
    
    @Autowired
    EsupOtpConfigurationProperties esupOtpConfigurationProperties;
            
	@RefreshScope
	@Bean
	public FlowDefinitionRegistry esupotpFlowRegistry() {
		final FlowDefinitionRegistryBuilder builder = new FlowDefinitionRegistryBuilder(this.applicationContext, this.builder);
		builder.setBasePath("classpath*:/webflow");
		builder.addFlowLocationPattern("/mfa-esupotp/*-webflow.xml");
		return builder.build();
	}

	@Bean
	public Action esupotpAuthenticationWebflowAction() {
		final EsupOtpAuthenticationWebflowAction a = new EsupOtpAuthenticationWebflowAction();
		a.setEsupOtpAuthenticationWebflowEventResolver(esupotpAuthenticationWebflowEventResolver());
		return a;
	}

	@Bean
	@RefreshScope public Action esupotpGetTransportsAction(EsupOtpService esupOtpService) {
		final EsupOtpGetTransportsAction a = new EsupOtpGetTransportsAction(applicationContext, esupOtpConfigurationProperties, esupOtpService);
		return a;
	}

	@Bean
	public CasWebflowEventResolver esupotpAuthenticationWebflowEventResolver() {
		
		CasWebflowEventResolutionConfigurationContext context = CasWebflowEventResolutionConfigurationContext.builder()
		            .casDelegatingWebflowEventResolver(initialAuthenticationAttemptWebflowEventResolver.getObject())
		            .authenticationContextValidator(authenticationContextValidator.getObject())
		            .authenticationSystemSupport(authenticationSystemSupport)
		            .centralAuthenticationService(centralAuthenticationService)
		            .servicesManager(servicesManager)
		            .ticketRegistrySupport(ticketRegistrySupport)
		            .warnCookieGenerator(warnCookieGenerator.getObject())
		            .authenticationRequestServiceSelectionStrategies(authenticationRequestServiceSelectionStrategies.getObject())
		            .registeredServiceAccessStrategyEnforcer(registeredServiceAccessStrategyEnforcer.getObject())
		            .casProperties(casProperties)
		            .singleSignOnParticipationStrategy(webflowSingleSignOnParticipationStrategy.getObject())
		            .ticketRegistry(ticketRegistry.getObject())
		            .applicationContext(applicationContext)
		            .authenticationEventExecutionPlan(authenticationEventExecutionPlan.getObject())
		            .build();
		 
		return new EsupOtpAuthenticationWebflowEventResolver(context);
	}
    
    @ConditionalOnMissingBean(name = "esupotpMultifactorWebflowConfigurer")
    @Bean
    @DependsOn("defaultWebflowConfigurer")
    public CasWebflowConfigurer esupotpMultifactorWebflowConfigurer() {
        final CasWebflowConfigurer w = new EsupOtpMultifactorWebflowConfigurer(flowBuilderServices, loginFlowDefinitionRegistry,
                esupotpFlowRegistry(), applicationContext, casProperties, MultifactorAuthenticationWebflowUtils.getMultifactorAuthenticationWebflowCustomizers(applicationContext));
        w.initialize();
        return w;
    }
    

    /**                                                                                                                                                                                                            
     * multifactor trust configuration.                                                                                                                                                                 
     */
    @ConditionalOnClass(value = MultifactorAuthenticationTrustStorage.class)
    @ConditionalOnProperty(prefix = "esupotp", name = "trustedDeviceEnabled", havingValue = "true", matchIfMissing = true)
    @Configuration("esupOtpMultifactorTrustConfiguration")
    public class EsupOtpMultifactorTrustConfiguration implements CasWebflowExecutionPlanConfigurer {

        @ConditionalOnMissingBean(name = "esupotpMultifactorTrustWebflowConfigurer")
        @Bean
        @DependsOn("defaultWebflowConfigurer")
        public CasWebflowConfigurer esupotpMultifactorTrustWebflowConfigurer() {
        	log.debug("esupotp.trustedDeviceEnabled true, esupotpMultifactorTrustWebflowConfigurer ok");
        	final CasWebflowConfigurer w =  new EsupOtpMultifactorTrustWebflowConfigurer(flowBuilderServices, loginFlowDefinitionRegistry,
                casProperties.getAuthn().getMfa().getTrusted().isDeviceRegistrationEnabled(), 
                esupOtpConfigurationProperties.getIsDeviceRegistrationRequired(),
                esupotpFlowRegistry(),
                applicationContext, casProperties, MultifactorAuthenticationWebflowUtils.getMultifactorAuthenticationWebflowCustomizers(applicationContext));
        	w.initialize();
            return w;
        }

        @Override
        public void configureWebflowExecutionPlan(final CasWebflowExecutionPlan plan) {
            plan.registerWebflowConfigurer(esupotpMultifactorTrustWebflowConfigurer());
        }
    }


}
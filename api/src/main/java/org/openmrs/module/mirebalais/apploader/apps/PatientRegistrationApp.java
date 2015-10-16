package org.openmrs.module.mirebalais.apploader.apps;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.addresshierarchy.AddressField;
import org.openmrs.module.addresshierarchy.AddressHierarchyLevel;
import org.openmrs.module.addresshierarchy.service.AddressHierarchyService;
import org.openmrs.module.appframework.domain.AppDescriptor;
import org.openmrs.module.mirebalais.MirebalaisConstants;
import org.openmrs.module.mirebalais.apploader.CustomAppLoaderConstants;
import org.openmrs.module.pihcore.config.Config;
import org.openmrs.module.pihcore.config.ConfigDescriptor;
import org.openmrs.module.pihcore.deploy.bundle.core.EncounterRoleBundle;
import org.openmrs.module.pihcore.metadata.core.EncounterTypes;
import org.openmrs.module.pihcore.metadata.core.PersonAttributeTypes;
import org.openmrs.module.registrationapp.model.DropdownWidget;
import org.openmrs.module.registrationapp.model.Field;
import org.openmrs.module.registrationapp.model.PersonAddressWithHierarchyWidget;
import org.openmrs.module.registrationapp.model.Question;
import org.openmrs.module.registrationapp.model.RegistrationAppConfig;
import org.openmrs.module.registrationapp.model.Section;
import org.openmrs.module.registrationapp.model.TextAreaWidget;
import org.openmrs.module.registrationapp.model.TextFieldWidget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class to help defining PatientRegistrationApp
 */
@Component
public class PatientRegistrationApp {

    @Autowired
    private ConceptService conceptService;

    public AppDescriptor getAppDescriptor(Config config) {
        AppDescriptor d = new AppDescriptor();
        d.setId(CustomAppLoaderConstants.Apps.PATIENT_REGISTRATION);
        d.setDescription("registrationapp.registerPatient");
        d.setLabel("registrationapp.app.registerPatient.label");
        d.setIcon("icon-user");
        d.setUrl("registrationapp/findPatient.page?appId=" + CustomAppLoaderConstants.Apps.PATIENT_REGISTRATION);
        d.setRequiredPrivilege("App: registrationapp.registerPatient");
        d.setConfig(toObjectNode(getRegistrationAppConfig(config)));
        return d;
    }

    public RegistrationAppConfig getRegistrationAppConfig(Config config) {
        RegistrationAppConfig c = new RegistrationAppConfig();
        c.setPatientDashboardLink(MirebalaisConstants.PATIENT_DASHBOARD_LINK);
        c.setRegistrationEncounter(EncounterTypes.PATIENT_REGISTRATION.uuid(), EncounterRoleBundle.EncounterRoles.ADMINISTRATIVE_CLERK);
        c.setAllowRetrospectiveEntry(true);
        c.setAllowUnknownPatients(config.getRegistrationConfig().isAllowUnknownPatients());
        c.setAllowManualIdentifier(config.getRegistrationConfig().isAllowManualEntryOfPrimaryIdentifier());
        c.setAfterCreatedUrl(config.getRegistrationConfig().getAfterCreatedUrl());
        c.setMatchingPatientsPropertiesToDisplay(config.getRegistrationConfig().getMatchingPatientsPropertiesToDisplay());
        c.setMaxPatientSearchResults(config.getRegistrationConfig().getMaxPatientMatchResults());
        addSections(c, config);
        return c;
    }

    public void addSections(RegistrationAppConfig c, Config config) {
        c.addSection(getDemographicsSection());
        c.addSection(getContactInfoSection(config));
        c.addSection(getSocialSection(config));
        c.addSection(getContactsSection(config));

        if (config.isComponentEnabled(CustomAppLoaderConstants.Components.ID_CARD_PRINTING)) {
            c.addSection(getIdentifierSection());
        }
    }

    public Section getDemographicsSection() {
        Section s = new Section();
        s.setId("demographics");
        s.setLabel("");
        s.addQuestion(getMothersNameQuestion());
        return s;
    }

    public Question getMothersNameQuestion() {
        Question q = new Question();
        q.setId("mothersFirstNameLabel");
        q.setLegend("zl.registration.patient.mothersFirstName.label");
        q.setHeader("zl.registration.patient.mothersFirstName.question");

        Field f = new Field();
        f.setFormFieldName("mothersFirstName");
        f.setType("personAttribute");
        f.setUuid(PersonAttributeTypes.MOTHERS_FIRST_NAME.uuid());
        f.setWidget(getTextFieldWidget());
        f.setCssClasses(Arrays.asList("required"));
        q.addField(f);

        return q;
    }

    public Section getContactInfoSection(Config config) {
        Section s = new Section();
        s.setId("contactInfo");
        s.setLabel("registrationapp.patient.contactInfo.label");
        s.addQuestion(getAddressQuestion(config));
        s.addQuestion(getTelephoneNumberQuestion());
        return s;
    }

    public Question getAddressQuestion(Config config) {
        Question q = new Question();
        q.setId("personAddressQuestion");
        q.setLegend("registrationapp.patient.address");
        q.setHeader("registrationapp.patient.address.question");

        Field f = new Field();
        f.setType("personAddress");

        // If there are address hierarchy levels configured, use the address hierarchy widget, otherwise use the standard address widget
        List<AddressHierarchyLevel> levels = Context.getService(AddressHierarchyService.class).getAddressHierarchyLevels();
        if (levels != null && levels.size() > 0) {
            q.setDisplayTemplate(getAddressHierarchyDisplayTemplate(levels));
            f.setWidget(getAddressHierarchyWidget(levels, null, true));
        }
        else {
            Map<String, String> m = new HashMap<String, String>();
            m.put("providerName", "uicommons");
            m.put("fragmentId", "field/personAddress");
            f.setWidget(toObjectNode(m));
        }

        q.addField(f);
        return q;
    }

    public Question getTelephoneNumberQuestion() {
        Question q = new Question();
        q.setId("phoneNumberLabel");
        q.setLegend("registrationapp.patient.phone.label");
        q.setHeader("registrationapp.patient.phone.question");

        Field f = new Field();
        f.setFormFieldName("phoneNumber");
        f.setType("personAttribute");
        f.setUuid(PersonAttributeTypes.TELEPHONE_NUMBER.uuid());
        f.setWidget(getTextFieldWidget());

        q.addField(f);
        return q;
    }

    public Section getSocialSection(Config config) {
        Section s = new Section();
        s.setId("social");
        s.setLabel("zl.registration.patient.social.label");
        s.addQuestion(getBirthplaceQuestion(config));
        s.addQuestion(getCivilStatusQuestion());
        s.addQuestion(getOccupationQuestion(config));
        if (config.getCountry() == ConfigDescriptor.Country.HAITI) { // TODO: Replace this with property in RegistrationConfig
            s.addQuestion(getReligionQuestion());
        }
        return s;
    }

    public Question getBirthplaceQuestion(Config config) {
        Question q = new Question();
        q.setId("birthplaceLabel");
        q.setHeader("zl.registration.patient.birthplace.question");
        q.setLegend("zl.registration.patient.birthplace.label");

        if (config.getCountry().equals(ConfigDescriptor.Country.HAITI)) {
            Field f = new Field();
            //f.setFormFieldName("obsgroup.PIH:PATIENT CONTACTS CONSTRUCT.obs.PIH:ADDRESS OF PATIENT CONTACT");
            f.setLabel("zl.registration.patient.birthplace.label");
            f.setType("personAddress");

            // If there are address hierarchy levels configured, use the address hierarchy widget, otherwise use the standard address widget
            List<AddressHierarchyLevel> levels = Context.getService(AddressHierarchyService.class).getAddressHierarchyLevels();
            if (levels != null && levels.size() > 0) {
                //q.setDisplayTemplate(getAddressHierarchyDisplayTemplate(levels));
                f.setWidget(getAddressHierarchyWidget(levels, getPlaceOfBirthAddressFieldMappings(config), true));
            }
            else {
                Map<String, String> m = new HashMap<String, String>();
                m.put("providerName", "uicommons");
                m.put("fragmentId", "field/personAddress");
                f.setWidget(toObjectNode(m));
            }
            q.addField(f);
        }
        else{
            Field f = new Field();
            f.setFormFieldName("obs.PIH:PLACE OF BIRTH");
            f.setType("obs");
            f.setWidget(getTextFieldWidget(50));
            q.addField(f);
        }
        return q;
    }

    public Question getCivilStatusQuestion() {
        Question q = new Question();
        q.setId("civilStatusLabel");
        q.setHeader("zl.registration.patient.civilStatus.question");
        q.setLegend("zl.registration.patient.civilStatus.label");

        Field f = new Field();
        f.setFormFieldName("obs.PIH:CIVIL STATUS");
        f.setType("obs");

        DropdownWidget w = new DropdownWidget();
        w.getConfig().setExpanded(true);
        w.getConfig().addOption("PIH:SINGLE OR A CHILD", "zl.registration.patient.civilStatus.single.label");
        w.getConfig().addOption("PIH:MARRIED", "zl.registration.patient.civilStatus.married.label");
        w.getConfig().addOption("PIH:LIVING WITH PARTNER", "zl.registration.patient.civilStatus.livingWithPartner.label");
        w.getConfig().addOption("PIH:SEPARATED", "zl.registration.patient.civilStatus.separated.label");
        w.getConfig().addOption("PIH:DIVORCED", "zl.registration.patient.civilStatus.divorced.label");
        w.getConfig().addOption("PIH:WIDOWED", "zl.registration.patient.civilStatus.widowed.label");
        f.setWidget(toObjectNode(w));

        q.addField(f);
        return q;
    }

    public Question getOccupationQuestion(Config config) {
        Question q = new Question();
        q.setId("occupationLabel");
        q.setLegend("zl.registration.patient.occupation.label");
        q.setHeader("zl.registration.patient.occupation.question");

        Field f = new Field();
        f.setFormFieldName("obs.PIH:Occupation");
        f.setType("obs");

        DropdownWidget w = new DropdownWidget();

        if (config.getCountry().equals(ConfigDescriptor.Country.HAITI)) {
            // ordered alphabetically in French, with Unemployed and Other last
            w.getConfig().addOption("PIH:SHEPHERD", "zl.registration.patient.occupation.shepherd.label");
            w.getConfig().addOption("PIH:DRIVER", "zl.registration.patient.occupation.driver.label");
            w.getConfig().addOption("PIH:COMMERCE", "zl.registration.patient.occupation.commerce.label");
            w.getConfig().addOption("PIH:FARMER", "zl.registration.patient.occupation.farmer.label");
            w.getConfig().addOption("CIEL:162944", "zl.registration.patient.occupation.civilServant.label");
            w.getConfig().addOption("PIH:MANUAL LABORER", "zl.registration.patient.occupation.manualLaborer.label");
            w.getConfig().addOption("PIH:HEALTH CARE WORKER", "zl.registration.patient.occupation.healthCareWorker.label");
            w.getConfig().addOption("PIH:MINER", "zl.registration.patient.occupation.miner.label");
            w.getConfig().addOption("PIH:1404", "zl.registration.patient.occupation.housework.label");
            w.getConfig().addOption("PIH:HOUSEWORK/FIELDWORK", "zl.registration.patient.occupation.houseworkFieldwork.label");
            w.getConfig().addOption("PIH:FACTORY WORKER", "zl.registration.patient.occupation.factoryWorker.label");
            w.getConfig().addOption("PIH:Teacher", "zl.registration.patient.occupation.teacher.label");
            w.getConfig().addOption("PIH:PROFESSIONAL", "zl.registration.patient.occupation.professional.label");
            w.getConfig().addOption("PIH:SHOP OWNER", "zl.registration.patient.occupation.shopOwner.label");
            w.getConfig().addOption("CIEL:159674", "zl.registration.patient.occupation.fisherman.label");
            w.getConfig().addOption("PIH:RETIRED", "zl.registration.patient.occupation.retired.label");
            w.getConfig().addOption("PIH:FRUIT OR VEGETABLE SELLER", "zl.registration.patient.occupation.fruitOrVegetableVendor.label");
            w.getConfig().addOption("CIEL:162945", "zl.registration.patient.occupation.marketVendor.label");
            w.getConfig().addOption("PIH:STUDENT", "zl.registration.patient.occupation.student.label");
            w.getConfig().addOption("PIH:UNEMPLOYED", "zl.registration.patient.occupation.unemployed.label");
            w.getConfig().addOption("PIH:OTHER NON-CODED", "zl.registration.patient.occupation.other.label");
        }
        else if (config.getCountry().equals(ConfigDescriptor.Country.LIBERIA) || (config.getCountry().equals(ConfigDescriptor.Country.SIERRA_LEONE))) {
            // ordered alphabetically in English, with Unemployed and Other last
            w.getConfig().addOption("PIH:BREWER", "zl.registration.patient.occupation.brewer.label");
            w.getConfig().addOption("CIEL:162944", "zl.registration.patient.occupation.civilServant.label");
            w.getConfig().addOption("PIH:COMMERCE", "zl.registration.patient.occupation.commerce.label");
            w.getConfig().addOption("PIH:DRIVER", "zl.registration.patient.occupation.driver.label");
            w.getConfig().addOption("PIH:FACTORY WORKER", "zl.registration.patient.occupation.factoryWorker.label");
            w.getConfig().addOption("PIH:FARMER", "zl.registration.patient.occupation.farmer.label");
            w.getConfig().addOption("CIEL:159674", "zl.registration.patient.occupation.fisherman.label");
            w.getConfig().addOption("PIH:FRUIT OR VEGETABLE SELLER", "zl.registration.patient.occupation.fruitOrVegetableVendor.label");
            w.getConfig().addOption("PIH:HEALTH CARE WORKER", "zl.registration.patient.occupation.healthCareWorker.label");
            w.getConfig().addOption("PIH:1404", "zl.registration.patient.occupation.housework.label");
            w.getConfig().addOption("PIH:HOUSEWORK/FIELDWORK", "zl.registration.patient.occupation.houseworkFieldwork.label");
            w.getConfig().addOption("PIH:MANUAL LABORER", "zl.registration.patient.occupation.manualLaborer.label");
            w.getConfig().addOption("CIEL:162945", "zl.registration.patient.occupation.marketVendor.label");
            w.getConfig().addOption("PIH:MINER", "zl.registration.patient.occupation.miner.label");
            w.getConfig().addOption("PIH:PROFESSIONAL", "zl.registration.patient.occupation.professional.label");
            w.getConfig().addOption("PIH:RETIRED", "zl.registration.patient.occupation.retired.label");
            w.getConfig().addOption("PIH:SHEPHERD", "zl.registration.patient.occupation.shepherd.label");
            w.getConfig().addOption("PIH:SHOP OWNER", "zl.registration.patient.occupation.shopOwner.label");
            w.getConfig().addOption("PIH:STUDENT", "zl.registration.patient.occupation.student.label");
            w.getConfig().addOption("PIH:Teacher", "zl.registration.patient.occupation.teacher.label");
            w.getConfig().addOption("PIH:UNEMPLOYED", "zl.registration.patient.occupation.unemployed.label");
            w.getConfig().addOption("PIH:OTHER NON-CODED", "zl.registration.patient.occupation.other.label");
        }

        w.getConfig().setExpanded(true);
        f.setWidget(toObjectNode(w));
        q.addField(f);

        return q;
    }

    public Question getReligionQuestion() {
        Question q = new Question();
        q.setId("religionLabel");
        q.setLegend("zl.registration.patient.religion.label");
        q.setHeader("zl.registration.patient.religion.question");

        Field f = new Field();
        f.setFormFieldName("obs.PIH:Religion");
        f.setType("obs");

        DropdownWidget w = new DropdownWidget();
        w.getConfig().setExpanded(true);
        w.getConfig().addOption("PIH:Voodoo", "zl.registration.patient.religion.voodoo.label");
        w.getConfig().addOption("PIH:Catholic", "zl.registration.patient.religion.catholic.label");
        w.getConfig().addOption("PIH:Baptist", "zl.registration.patient.religion.baptist.label");
        w.getConfig().addOption("PIH:Islam", "zl.registration.patient.religion.islam.label");
        w.getConfig().addOption("PIH:Pentecostal", "zl.registration.patient.religion.pentecostal.label");
        w.getConfig().addOption("PIH:Seventh Day Adventist", "zl.registration.patient.religion.adventist.label");
        w.getConfig().addOption("PIH:Jehovah's Witness", "zl.registration.patient.religion.jehovahsWitness.label");
        w.getConfig().addOption("PIH:OTHER NON-CODED", "zl.registration.patient.religion.other.label");
        f.setWidget(toObjectNode(w));

        q.addField(f);
        return q;
    }

    public Section getContactsSection(Config config) {
        Section s = new Section();
        s.setId("contacts");
        s.setLabel("zl.registration.patient.contactPerson.label");

        boolean required = config.getCountry() == ConfigDescriptor.Country.HAITI; // TODO: Replace with property in RegistrationConfig

        s.addQuestion(getContactNameAndRelationship(config, required));
        s.addQuestion(getContactAddress(config, required));
        s.addQuestion(getContactPhoneNumber(config, required));

        return s;
    }

    public Question getContactNameAndRelationship(Config config, boolean required) {
        Question q = new Question();
        q.setId("contactNameLabel");
        q.setLegend("zl.registration.patient.contactPerson.name.label");
        q.setHeader("zl.registration.patient.contactPerson.name.question");

        {
            Field f = new Field();
            f.setFormFieldName("obsgroup.PIH:PATIENT CONTACTS CONSTRUCT.obs.PIH:NAMES AND FIRSTNAMES OF CONTACT");
            f.setLabel("zl.registration.patient.contactPerson.contactName.question");
            f.setType("obsgroup");
            if (required) { f.setCssClasses(Arrays.asList("required")); }
            f.setWidget(getTextFieldWidget(30));
            q.addField(f);
        }
        {
            Field f = new Field();
            f.setFormFieldName("obsgroup.PIH:PATIENT CONTACTS CONSTRUCT.obs.PIH:RELATIONSHIPS OF CONTACT");
            f.setLabel("zl.registration.patient.contactPerson.relationships.label");
            f.setType("obsgroup");
            if (required) { f.setCssClasses(Arrays.asList("required")); }
            f.setWidget(getTextFieldWidget(30));
            q.addField(f);
        }

        return q;
    }

    public Question getContactAddress(Config config, boolean required) {

        Question q = new Question();
        q.setId("contactQuestionLabel");
        q.setHeader("zl.registration.patient.contactPerson.address.question");
        q.setLegend("zl.registration.patient.contactPerson.address.label");

        if (config.getCountry().equals(ConfigDescriptor.Country.HAITI)) {
            Field f = new Field();
            //f.setFormFieldName("obsgroup.PIH:PATIENT CONTACTS CONSTRUCT.obs.PIH:ADDRESS OF PATIENT CONTACT");
            f.setLabel("zl.registration.patient.contactPerson.address.label");
            f.setType("personAddress");

            // If there are address hierarchy levels configured, use the address hierarchy widget, otherwise use the standard address widget
            List<AddressHierarchyLevel> levels = Context.getService(AddressHierarchyService.class).getAddressHierarchyLevels();
            if (levels != null && levels.size() > 0) {
                //q.setDisplayTemplate(getAddressHierarchyDisplayTemplate(levels));
                f.setWidget(getAddressHierarchyWidget(levels, getContactAddressFieldMappings(config), true));
            }
            else {
                Map<String, String> m = new HashMap<String, String>();
                m.put("providerName", "uicommons");
                m.put("fragmentId", "field/personAddress");
                f.setWidget(toObjectNode(m));
                if (required) { f.setCssClasses(Arrays.asList("required")); }
            }
            q.addField(f);
        }
        else{
            Field f = new Field();
            f.setFormFieldName("obsgroup.PIH:PATIENT CONTACTS CONSTRUCT.obs.PIH:ADDRESS OF PATIENT CONTACT");
            f.setLabel("zl.registration.patient.contactPerson.address.label");
            f.setType("obsgroup");
            f.setWidget(getTextAreaWidget(250));
            if (required) { f.setCssClasses(Arrays.asList("required")); }
            q.addField(f);
        }

        return q;
    }

    public Question getContactPhoneNumber(Config config, Boolean required) {

        Question q = new Question();
        q.setId("contactPhoneNumberQuestionLabel");
        q.setHeader("zl.registration.patient.contactPerson.phonenumber.question");
        q.setLegend("zl.registration.patient.contactPerson.phonenumber.label");

        {
            Field f = new Field();
            f.setFormFieldName("obsgroup.PIH:PATIENT CONTACTS CONSTRUCT.obs.PIH:TELEPHONE NUMBER OF CONTACT");
            f.setLabel("registrationapp.patient.phone.label");
            f.setType("obsgroup");
            if (required) { f.setCssClasses(Arrays.asList("required")); }
            f.setWidget(getTextFieldWidget(30));
            q.addField(f);
        }

        return q;
    }

    public Section getIdentifierSection() {
        Section s = new Section();
        s.setId("patient-identification-section");
        s.setLabel("registrationapp.patient.identifiers.label");
        s.addQuestion(getIdCardPrintQuestion());
        return s;
    }

    public Question getIdCardPrintQuestion() {
        Question q = new Question();
        q.setId("idcardLabel");
        q.setLegend("zl.registration.patient.idcard.label");
        q.setHeader("zl.registration.patient.idcard.question");

        Field f = new Field();
        f.setFormFieldName("obs.PIH:ID Card Printing Requested");
        f.setType("obs");

        DropdownWidget w = new DropdownWidget();
        w.getConfig().setExpanded(true);
        w.getConfig().setHideEmptyLabel(true);
        w.getConfig().setInitialValue("PIH:YES");
        w.getConfig().addOption("PIH:YES", "emr.yes");
        w.getConfig().addOption("PIH:NO", "emr.no");
        f.setWidget(toObjectNode(w));

        q.addField(f);
        return q;
    }

    protected void populateDropdownWithConceptAnswers(DropdownWidget d, Concept questionConcept) {

        List<ConceptAnswer> conceptAnswers = new ArrayList<ConceptAnswer>(questionConcept.getAnswers());

        Collections.sort(conceptAnswers, new Comparator<ConceptAnswer>() {
            @Override
            public int compare(ConceptAnswer answer1, ConceptAnswer answer2) {
                return answer1.getAnswerConcept().getName().toString().compareTo(answer2.getAnswerConcept().getName().toString());
            }
        });

        for (ConceptAnswer conceptAnswer : conceptAnswers) {
            d.getConfig().addOption(conceptAnswer.getAnswerConcept().getId().toString(), conceptAnswer.getAnswerConcept().getName().toString());
        }

    }

    protected String getAddressHierarchyDisplayTemplate(List<AddressHierarchyLevel> levels) {
        // We want the display template to hide country, and show a dash if the lowest free-text level is missing
        // TODO: Is this what we want?  Should we show a dash for all empty, non-required fields?
        StringBuilder displayTemplate = new StringBuilder();
        displayTemplate.append("{{nvl field.[" + levels.size() + "] '-'}}");
        for (int i = levels.size() - 1; i >= 2; i--) {
            displayTemplate.append(", {{field.[" + i + "]}}");
        }

        return displayTemplate.toString();
    }

    protected ObjectNode getAddressHierarchyWidget(List<AddressHierarchyLevel> levels, Map<String,String> fieldMappings, Boolean required) {
        // Make the lowest level of the hierarchy free-text, and the second-lowest for the shortcut
        String shortCutFor = levels.get(levels.size()-2).getAddressField().getName();
        String manualField = levels.get(levels.size()-1).getAddressField().getName();

        PersonAddressWithHierarchyWidget w = new PersonAddressWithHierarchyWidget();
        w.getConfig().setShortcutFor(shortCutFor);
        w.getConfig().addManualField(manualField);
        w.getConfig().setFieldMappings(fieldMappings);
        w.getConfig().setRequired(required);

        return toObjectNode(w);
    }

    protected Map<String,String> getContactAddressFieldMappings(Config config) {
        // Haiti-specific
        Map<String,String> fieldMappings = new HashMap<String, String>();
        fieldMappings.put(AddressField.COUNTRY.getName(), "obsgroup.PIH:PATIENT CONTACTS CONSTRUCT.obs.PIH:Country");
        fieldMappings.put(AddressField.STATE_PROVINCE.getName(), "obsgroup.PIH:PATIENT CONTACTS CONSTRUCT.obs.PIH:State Province");
        fieldMappings.put(AddressField.CITY_VILLAGE.getName(), "obsgroup.PIH:PATIENT CONTACTS CONSTRUCT.obs.PIH:City Village");
        fieldMappings.put(AddressField.ADDRESS_3.getName(), "obsgroup.PIH:PATIENT CONTACTS CONSTRUCT.obs.PIH:Address3");
        fieldMappings.put(AddressField.ADDRESS_1.getName(), "obsgroup.PIH:PATIENT CONTACTS CONSTRUCT.obs.PIH:Address1");
        fieldMappings.put(AddressField.ADDRESS_2.getName(), "obsgroup.PIH:PATIENT CONTACTS CONSTRUCT.obs.PIH:Address2");
        return fieldMappings;
    }

    protected Map<String,String> getPlaceOfBirthAddressFieldMappings(Config config) {
        // Haiti-specific
        Map<String,String> fieldMappings = new HashMap<String, String>();
        fieldMappings.put(AddressField.COUNTRY.getName(), "obsgroup.PIH:Birthplace address construct.obs.PIH:Country");
        fieldMappings.put(AddressField.STATE_PROVINCE.getName(), "obsgroup.PIH:Birthplace address construct.obs.PIH:State Province");
        fieldMappings.put(AddressField.CITY_VILLAGE.getName(), "obsgroup.PIH:Birthplace address construct.obs.PIH:City Village");
        fieldMappings.put(AddressField.ADDRESS_3.getName(), "obsgroup.PIH:Birthplace address construct.obs.PIH:Address3");
        fieldMappings.put(AddressField.ADDRESS_1.getName(), "obsgroup.PIH:Birthplace address construct.obs.PIH:Address1");
        fieldMappings.put(AddressField.ADDRESS_2.getName(), "obsgroup.PIH:Birthplace address construct.obs.PIH:Address2");
        return fieldMappings;
    }


    protected ObjectNode getTextFieldWidget() {
        return getTextFieldWidget(null);
    }

    protected ObjectNode getTextFieldWidget(Integer size) {
        TextFieldWidget w = new TextFieldWidget();
        if (size != null) {
            w.getConfig().setSize(size);
        }
        return toObjectNode(w);
    }

    protected ObjectNode getTextAreaWidget(Integer maxLength) {
        TextAreaWidget w = new TextAreaWidget();
        if (maxLength != null) {
            w.getConfig().setMaxlength(maxLength);
        }
        return toObjectNode(w);
    }

    protected ObjectNode toObjectNode(Object o) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.getSerializationConfig().setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
        return mapper.convertValue(o, ObjectNode.class);
    }
}

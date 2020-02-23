package org.egov.waterConnection.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.egov.common.contract.request.RequestInfo;
import org.egov.mdms.model.MasterDetail;
import org.egov.mdms.model.MdmsCriteria;
import org.egov.mdms.model.MdmsCriteriaReq;
import org.egov.mdms.model.ModuleDetail;
import org.egov.tracer.model.CustomException;
import org.egov.waterConnection.config.WSConfiguration;
import org.egov.waterConnection.model.AuditDetails;
import org.egov.waterConnection.model.Property;
import org.egov.waterConnection.model.PropertyCriteria;
import org.egov.waterConnection.model.PropertyRequest;
import org.egov.waterConnection.model.PropertyResponse;
import org.egov.waterConnection.model.RequestInfoWrapper;
import org.egov.waterConnection.model.WaterConnectionRequest;
import org.egov.waterConnection.model.workflow.BusinessService;
import org.egov.waterConnection.model.SearchCriteria;
import org.egov.waterConnection.model.WaterConnection;
import org.egov.waterConnection.repository.ServiceRequestRepository;
import org.egov.waterConnection.workflow.WorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WaterServicesUtil {

	@Autowired
	ObjectMapper objectMapper;
	
	@Autowired
	private WSConfiguration config;

	private ServiceRequestRepository serviceRequestRepository;

	@Value("${egov.property.service.host}")
	private String propertyHost;

	@Value("${egov.property.createendpoint}")
	private String createPropertyEndPoint;

	@Value("${egov.property.searchendpoint}")
	private String searchPropertyEndPoint;

	@Autowired
	public WaterServicesUtil(ServiceRequestRepository serviceRequestRepository) {
		this.serviceRequestRepository = serviceRequestRepository;

	}
	@Autowired
	private WorkflowService workflowService;

	/**
     * Method to return auditDetails for create/update flows
     *
     * @param by
     * @param isCreate
     * @return AuditDetails
     */
    public AuditDetails getAuditDetails(String by, Boolean isCreate) {
        Long time = System.currentTimeMillis();
        if(isCreate)
            return AuditDetails.builder().createdBy(by).lastModifiedBy(by).createdTime(time).lastModifiedTime(time).build();
        else
            return AuditDetails.builder().lastModifiedBy(by).lastModifiedTime(time).build();
    }
    
	/**
	 * 
	 * @param waterConnectionRequest
	 *            WaterConnectionRequest containing property
	 * @return List of Property
	 */
	public List<Property> propertySearch(WaterConnectionRequest waterConnectionRequest) {
		Set<String> propertyIds = new HashSet<>();
		List<Property> propertyList = new ArrayList<>();
		PropertyCriteria propertyCriteria = new PropertyCriteria();
		HashMap<String, Object> propertyRequestObj = new HashMap<>();
		propertyIds.add(waterConnectionRequest.getWaterConnection().getProperty().getPropertyId());
		propertyCriteria.setPropertyIds(propertyIds);
		propertyRequestObj.put("RequestInfoWrapper",
				getPropertyRequestInfoWrapperSearch(new RequestInfoWrapper(), waterConnectionRequest.getRequestInfo()));
		propertyRequestObj.put("PropertyCriteria", propertyCriteria);
		Object result = serviceRequestRepository.fetchResult(
				getPropURLForCreate(waterConnectionRequest.getWaterConnection().getProperty().getTenantId(),
						waterConnectionRequest.getWaterConnection().getProperty().getPropertyId()),
				RequestInfoWrapper.builder().requestInfo(waterConnectionRequest.getRequestInfo()).build());
		propertyList = getPropertyDetails(result);
		if (propertyList == null || propertyList.isEmpty()) {
			throw new CustomException("INCORRECT PROPERTY ID", "WATER CONNECTION CAN NOT BE CREATED");
		}
		return propertyList;
	}
	
	public List<Property> propertySearchForCitizen(RequestInfo requestInfo) {
		Set<String> propertyIds = new HashSet<>();
		List<Property> propertyList = new ArrayList<>();
		PropertyCriteria propertyCriteria = new PropertyCriteria();
		HashMap<String, Object> propertyRequestObj = new HashMap<>();
		propertyCriteria.setPropertyIds(propertyIds);
//		Object result = serviceRequestRepository.fetchResult(
//				getPropURLForCreate(waterConnectionRequest.getWaterConnection().getProperty().getTenantId(),
//						waterConnectionRequest.getWaterConnection().getProperty().getPropertyId()),
//				RequestInfoWrapper.builder().requestInfo(requestInfo).build());
//		propertyList = getPropertyDetails(result);
		if (propertyList == null || propertyList.isEmpty()) {
			throw new CustomException("INCORRECT PROPERTY ID", "WATER CONNECTION CAN NOT BE CREATED");
		}
		return propertyList;
	}

	/**
	 * 
	 * @param waterConnectionRequest
	 * @return Created property list
	 */
	public List<Property> createPropertyRequest(WaterConnectionRequest waterConnectionRequest) {
		List<Property> propertyList = new ArrayList<>();
		propertyList.add(waterConnectionRequest.getWaterConnection().getProperty());
		PropertyRequest propertyReq = getPropertyRequest(waterConnectionRequest.getRequestInfo(),
				waterConnectionRequest.getWaterConnection().getProperty());
		Object result = serviceRequestRepository.fetchResult(getPropertyCreateURL(), propertyReq);
		return getPropertyDetails(result);
	}

	/**
	 * 
	 * @param waterConnectionSearchCriteria
	 *            WaterConnectionSearchCriteria containing search criteria on
	 *            water connection
	 * @param requestInfo
	 * @return List of property matching on given criteria
	 */
	public List<Property> propertySearchOnCriteria(SearchCriteria waterConnectionSearchCriteria,
			RequestInfo requestInfo) {
//		if ((waterConnectionSearchCriteria.getTenantId() == null
//				|| waterConnectionSearchCriteria.getTenantId().isEmpty())) {
//			throw new CustomException("INVALID SEARCH", "TENANT ID NOT PRESENT");
//		}
		if ((waterConnectionSearchCriteria.getMobileNumber() == null
				|| waterConnectionSearchCriteria.getMobileNumber().isEmpty())) {
			return Collections.emptyList();
		}
		PropertyCriteria propertyCriteria = new PropertyCriteria();
		if (waterConnectionSearchCriteria.getTenantId() != null
				&& !waterConnectionSearchCriteria.getTenantId().isEmpty()) {
			propertyCriteria.setTenantId(waterConnectionSearchCriteria.getTenantId());
		}
		if (waterConnectionSearchCriteria.getMobileNumber() != null
				&& !waterConnectionSearchCriteria.getMobileNumber().isEmpty()) {
			propertyCriteria.setMobileNumber(waterConnectionSearchCriteria.getMobileNumber());
		}
		Object result = serviceRequestRepository.fetchResult(
				getPropURL(waterConnectionSearchCriteria.getTenantId(),
						waterConnectionSearchCriteria.getMobileNumber()),
				RequestInfoWrapper.builder().requestInfo(requestInfo).build());
		return getPropertyDetails(result);
	}
	
	/**
	 * 
	 * @param tenantId
	 * @param propertyId
	 * @param requestInfo
	 * @return List of Property
	 */
	public List<Property> searchPropertyOnId(String tenantId, String propertyIds, RequestInfo requestInfo){
		StringBuilder propertySearhURL = getPropURLForCreate(tenantId, propertyIds);
		Object result = serviceRequestRepository.fetchResult(propertySearhURL,RequestInfoWrapper.builder().requestInfo(requestInfo).build());
		return getPropertyDetails(result);
	}
	private RequestInfoWrapper getPropertyRequestInfoWrapperSearch(RequestInfoWrapper requestInfoWrapper,
			RequestInfo requestInfo) {
		RequestInfoWrapper requestInfoWrapper_new = RequestInfoWrapper.builder().requestInfo(requestInfo).build();
		return requestInfoWrapper_new;
	}

	/**
	 * 
	 * @param result
	 *            Response object from property service call
	 * @return List of property
	 */
	private List<Property> getPropertyDetails(Object result) {

		try {
			PropertyResponse propertyResponse = objectMapper.convertValue(result, PropertyResponse.class);
			return propertyResponse.getProperties();
		} catch (Exception ex) {
			throw new CustomException("PARSING ERROR", "The property json cannot be parsed");
		}
	}

	private PropertyRequest getPropertyRequest(RequestInfo requestInfo, Property propertyList) {
		PropertyRequest propertyReq = PropertyRequest.builder().requestInfo(requestInfo).property(propertyList).build();
		return propertyReq;
	}

	public StringBuilder getPropertyCreateURL() {
		return new StringBuilder().append(propertyHost).append(createPropertyEndPoint);
	}

	public StringBuilder getPropertyURL() {
		return new StringBuilder().append(propertyHost).append(searchPropertyEndPoint);
	}

	public MdmsCriteriaReq prepareMdMsRequest(String tenantId, String moduleName, List<String> names, String filter,
			RequestInfo requestInfo) {
		List<MasterDetail> masterDetails = new ArrayList<>();
		names.forEach(name -> {
			masterDetails.add(MasterDetail.builder().name(name).filter(filter).build());
		});
		ModuleDetail moduleDetail = ModuleDetail.builder().moduleName(moduleName).masterDetails(masterDetails).build();
		List<ModuleDetail> moduleDetails = new ArrayList<>();
		moduleDetails.add(moduleDetail);
		MdmsCriteria mdmsCriteria = MdmsCriteria.builder().tenantId(tenantId).moduleDetails(moduleDetails).build();
		return MdmsCriteriaReq.builder().requestInfo(requestInfo).mdmsCriteria(mdmsCriteria).build();
	}

	/**
	 * 
	 * @return search url for property search
	 */
	private String getpropertySearchURLForMobileSearch() {
		StringBuilder url = new StringBuilder(getPropertyURL());
		url.append("?");
		url.append("tenantId=");
		url.append("{1}");
		url.append("&");
		url.append("mobileNumber=");
		url.append("{2}");
		return url.toString();
	}
	
	/**
	 * 
	 * @return search url for property search
	 */
	private String getpropertySearchURLForMobileSearchCitizen() {
		StringBuilder url = new StringBuilder(getPropertyURL());
		url.append("?");
		url.append("mobileNumber=");
		url.append("{2}");
		return url.toString();
	}

	private StringBuilder getPropURL(String tenantId, String mobileNumber) {
		String url = getpropertySearchURLForMobileSearchCitizen();
		if(tenantId != null)
			url = getpropertySearchURLForMobileSearch();
		if (url.indexOf("{1}") > 0)
			url = url.replace("{1}", tenantId);
		if (url.indexOf("{2}") > 0)
			url = url.replace("{2}", mobileNumber);
		return new StringBuilder(url);
	}
	
	/**
	 * 
	 * @return search url for property search employee
	 */
	private String getPropertySearchURLForEmployee() {
		StringBuilder url = new StringBuilder(getPropertyURL());
		url.append("?");
		url.append("tenantId=");
		url.append("{1}");
		url.append("&");
		url.append("propertyIds=");
		url.append("{2}");
		return url.toString();
	}
	
	/**
	 * 
	 * @return search url for property search citizen
	 */
	private String getPropertySearchURLForCitizen() {
		StringBuilder url = new StringBuilder(getPropertyURL());
		url.append("?");
		url.append("propertyIds=");
		url.append("{2}");
		return url.toString();
	}
	
	
	private StringBuilder getPropURLForCreate(String tenantId, String propertyIds) {
		String url = getPropertySearchURLForCitizen();
		if (tenantId != null)
			url = getPropertySearchURLForEmployee();
		if (url.indexOf("{1}") > 0)
			url = url.replace("{1}", tenantId);
		if (url.indexOf("{2}") > 0)
			url = url.replace("{2}", propertyIds);
		return new StringBuilder(url);
	}
	
	/**
	 *
	 * @param businessService
	 * @param searchresult
	 * @return true if state updatable is true else false
	 */
	public boolean getStatusForUpdate(BusinessService businessService, WaterConnection searchresult) {
		return workflowService.isStateUpdatable(searchresult.getApplicationStatus().name(), businessService);
	}
	/**
	 * 
	 * @return URL of calculator service
	 */
	public StringBuilder getCalculatorURL() {
		StringBuilder builder = new StringBuilder();
		return builder.append(config.getCalculatorHost()).append(config.getCalculateEndpoint());
	}
}
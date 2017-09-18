/*
 * oxNotify is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */

package org.gluu.oxnotify.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.gluu.oxnotify.exception.ConfigurationException;
import org.gluu.oxnotify.model.PushPlatform;
import org.gluu.oxnotify.model.conf.AccessConfiguration;
import org.gluu.oxnotify.model.conf.ClientConfiguration;
import org.gluu.oxnotify.model.conf.Configuration;
import org.gluu.oxnotify.model.conf.PlatformConfiguration;
import org.gluu.oxnotify.model.sns.ClientData;
import org.slf4j.Logger;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSAsync;
import com.amazonaws.services.sns.AmazonSNSAsyncClientBuilder;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;

/**
 * @author Yuriy Movchan
 * @version September 15, 2017
 */
@ApplicationScoped
@Named
public class ApplicationService {

	@Inject
	private Logger log;

	@Inject
	private Configuration configuration;

	@Inject
	private AccessConfiguration accessConfiguration;

	private Map<String, ClientData> platormClients;

	private Map<String, ClientConfiguration> accessClients;

	@PostConstruct
	public void create() {
		this.platormClients = new HashMap<String, ClientData>();
		this.accessClients = new HashMap<String, ClientConfiguration>();
	}

	public void init() {
		initPlatformClients();
		initAccessClients();
	}

	public ClientConfiguration getAccessClient(String accessKeyId, String secretAccessKey) {
		if ((accessKeyId == null) || (secretAccessKey == null)) {
			log.error("Access key or secret is empty");
			return null;
		}

		ClientConfiguration clientConfiguration = this.accessClients.get(accessKeyId);
		if (clientConfiguration == null) {
			log.error("Failed to find client '{}' configuration", accessKeyId);
			return null;
		}

		if (!secretAccessKey.equals(clientConfiguration.getSecretAccessKey())) {
			log.error("Secret access key is invalid for client '{}'", accessKeyId);
			return null;
		}

		return clientConfiguration;
	}

	public ClientData getClientDataByPlatformId(String platformId) {
		if (platformId == null) {
			log.error("Request platform is empty");
			return null;
		}

		ClientData clientData = this.platormClients.get(platformId);
		if (clientData == null) {
			log.error("Failed to find client data for platform '{}'", platformId);
			return null;
		}

		return clientData;
	}

	private void initPlatformClients() {
		List<PlatformConfiguration> platformConfigurations = configuration.getPlatformConfigurations();
		if ((platformConfigurations == null) || platformConfigurations.isEmpty()) {
			log.error("List of platforms is empty!");
			return;
		}

		for (PlatformConfiguration platformConfiguration : platformConfigurations) {
			if (platformConfiguration.isEnabled()) {
				ClientData clientData = createClientData(platformConfiguration);
				if (clientData != null) {
					this.platormClients.put(platformConfiguration.getPlatformId().toLowerCase(), clientData);
				}
			}
		}

		log.info("Loaded configurations for '{}' clients", this.platormClients.size());
	}

	private void initAccessClients() {
		List<ClientConfiguration> clientConfiguratios = accessConfiguration.getClientConfigurations();
		if ((clientConfiguratios == null) || clientConfiguratios.isEmpty()) {
			log.error("List of clients is empty!");
			return;
		}

		for (ClientConfiguration clientConfiguration : clientConfiguratios) {
			if (clientConfiguration.isEnabled()) {
				this.accessClients.put(clientConfiguration.getAccessKeyId().toLowerCase(), clientConfiguration);
			}
		}

		log.info("Loaded configurations for '{}' access clients", this.accessClients.size());
	}

	private ClientData createClientData(PlatformConfiguration platformConfiguration) {
		ClientData clientData = null;
		try {
			BasicAWSCredentials credentials = new BasicAWSCredentials(platformConfiguration.getAccessKeyId(),
					platformConfiguration.getSecretAccessKey());
			AmazonSNSClientBuilder snsClientBuilder = AmazonSNSClientBuilder.standard();
			AmazonSNS amazonSNS = snsClientBuilder.withCredentials(new AWSStaticCredentialsProvider(credentials))
					.withRegion(platformConfiguration.getRegion()).build();
			clientData = new ClientData(amazonSNS, platformConfiguration.getPlatform(),
					platformConfiguration.getPlatformArn());
		} catch (Exception ex) {
			log.error("Faield to create client", ex);
		}

		return clientData;
	}

}

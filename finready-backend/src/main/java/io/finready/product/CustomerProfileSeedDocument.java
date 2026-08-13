package io.finready.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** seed/customer_profiles.json 의 루트 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerProfileSeedDocument(
		List<CustomerProfileSeedData> customerProfiles
) {
}

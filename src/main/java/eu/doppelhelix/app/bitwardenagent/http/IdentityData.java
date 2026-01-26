/*
 * Copyright 2026 matthias.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.doppelhelix.app.bitwardenagent.http;

public record IdentityData(
        String title,
        String firstName,
        String middleName,
        String lastName,
        String address1,
        String address2,
        String address3,
        String city,
        String state,
        String postalCode,
        String country,
        String company,
        String email,
        String phone,
        String ssn,
        String username,
        String passportNumber,
        String licenseNumber
        ) {

}

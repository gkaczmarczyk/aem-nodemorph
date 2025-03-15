/*
 * Copyright © 2025 Gregory Kaczmarczyk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package co.acu.nodemorph.core.dto;

public class UpdateResult {
    public String path;
    public String action;
    public String status;
    public String message;

    public UpdateResult(String path, String action, String status) {
        this.path = path;
        this.action = action;
        this.status = status;
    }

    public UpdateResult(String path, String action, String status, String message) {
        this.path = path;
        this.action = action;
        this.status = status;
        this.message = message;
    }

}

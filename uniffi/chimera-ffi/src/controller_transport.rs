use crate::ChimeraError;
use crate::controller::ClashController;

#[cfg(unix)]
use http_body_util::{BodyExt, Full};
#[cfg(unix)]
use hyper::Request;
#[cfg(unix)]
use hyper::body::Bytes;
#[cfg(unix)]
use hyper_util::client::legacy::Client;
#[cfg(unix)]
use hyper_util::rt::TokioExecutor;
#[cfg(unix)]
use hyperlocal::{UnixConnector, Uri as UnixUri};

#[cfg(unix)]
pub(crate) type UnixClient = Client<UnixConnector, Full<Bytes>>;

impl ClashController {
    pub(crate) fn for_socket_path(socket_path: String) -> Self {
        Self {
            socket_path,
            #[cfg(unix)]
            client: Client::builder(TokioExecutor::new()).build(UnixConnector),
        }
    }

    async fn do_request(
        &self,
        method: &str,
        path: &str,
        body: Option<Vec<u8>>,
    ) -> Result<hyper::body::Bytes, ChimeraError> {
        #[cfg(unix)]
        {
            let uri: hyper::Uri = UnixUri::new(&self.socket_path, path).into();

            let request_builder = Request::builder()
                .uri(uri)
                .method(method)
                .header("Content-Type", "application/json");

            let request = if let Some(body_data) = body {
                request_builder
                    .body(Full::new(Bytes::from(body_data)))
                    .map_err(|error| ChimeraError::Runtime {
                        details: format!("failed to build request with body: {error}"),
                    })?
            } else {
                request_builder
                    .body(Full::new(Bytes::new()))
                    .map_err(|error| ChimeraError::Runtime {
                        details: format!("failed to build request: {error}"),
                    })?
            };

            let response = self
                .client
                .request(request)
                .await
                .map_err(|error| ChimeraError::Runtime {
                    details: format!("controller request failed: {error}"),
                })
                .inspect_err(|error| tracing::error!("{error}"))?;

            if !response.status().is_success() {
                tracing::error!("controller http status error: {}", response.status());
                return Err(ChimeraError::Runtime {
                    details: format!("controller http status error: {}", response.status()),
                });
            }

            response
                .into_body()
                .collect()
                .await
                .map_err(|error| ChimeraError::Runtime {
                    details: format!("failed to read controller response: {error}"),
                })
                .map(|body| body.to_bytes())
        }
        #[cfg(not(unix))]
        {
            let _ = (method, path, body);
            Err(ChimeraError::Runtime {
                details: "unix domain socket controller is unavailable on this platform"
                    .to_string(),
            })
        }
    }

    pub(crate) async fn request_no_response(
        &self,
        method: &str,
        path: &str,
        body: Option<Vec<u8>>,
    ) -> Result<(), ChimeraError> {
        self.do_request(method, path, body).await.map(|_| ())
    }

    pub(crate) async fn request<T>(
        &self,
        method: &str,
        path: &str,
        body: Option<Vec<u8>>,
    ) -> Result<T, ChimeraError>
    where
        T: serde::de::DeserializeOwned,
    {
        let body_bytes = self.do_request(method, path, body).await?;
        serde_json::from_slice(&body_bytes).map_err(|error| ChimeraError::Runtime {
            details: format!("failed to decode controller response: {error}"),
        })
    }
}

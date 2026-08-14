use std::{
    fs,
    io::{Read, Write},
    net::{TcpListener, TcpStream},
    path::PathBuf,
    sync::atomic::{AtomicU64, Ordering},
    thread,
    time::{Duration, Instant},
};

use crate::util::{DownloadLimits, download_file_with_progress_and_limits};

#[test]
fn download_follows_redirects_within_limit() {
    let (url, server) = spawn_server(2, |request_index, _path| {
        if request_index == 0 {
            redirect_response("/profile")
        } else {
            ok_response("mixed-port: 7890\n")
        }
    });
    let output = unique_temp_path("redirect-success.yaml");

    let result = runtime().block_on(download_file_with_progress_and_limits(
        url,
        output.to_string_lossy().into_owned(),
        None,
        None,
        None,
        test_limits(Duration::from_secs(2), 2),
    ));

    let result = result.unwrap();
    assert!(result.success);
    assert_eq!("mixed-port: 7890\n", fs::read_to_string(&output).unwrap());
    assert_eq!(2, server.join().unwrap());
    let _ = fs::remove_file(output);
}

#[test]
fn download_rejects_redirect_chain_over_limit() {
    let (url, server) = spawn_server(3, |request_index, _path| {
        redirect_response(&format!("/redirect-{}", request_index + 1))
    });
    let output = unique_temp_path("redirect-limit.yaml");

    let error = match runtime().block_on(download_file_with_progress_and_limits(
        url,
        output.to_string_lossy().into_owned(),
        None,
        None,
        None,
        test_limits(Duration::from_secs(2), 2),
    )) {
        Ok(_) => panic!("redirect chain unexpectedly succeeded"),
        Err(error) => error,
    };

    assert_eq!(
        "download redirect limit exceeded (maximum 2)",
        error.to_string()
    );
    assert!(!output.exists());
    assert_eq!(3, server.join().unwrap());
}

#[test]
fn download_reports_total_request_timeout() {
    let (url, server) = spawn_server(1, |_request_index, _path| {
        thread::sleep(Duration::from_millis(250));
        ok_response("mixed-port: 7890\n")
    });
    let output = unique_temp_path("timeout.yaml");

    let error = match runtime().block_on(download_file_with_progress_and_limits(
        url,
        output.to_string_lossy().into_owned(),
        None,
        None,
        None,
        test_limits(Duration::from_millis(50), 1),
    )) {
        Ok(_) => panic!("slow response unexpectedly succeeded"),
        Err(error) => error,
    };

    assert_eq!(
        "download request timed out after 0.05 seconds",
        error.to_string()
    );
    assert!(!output.exists());
    assert_eq!(1, server.join().unwrap());
}

#[test]
fn default_download_limits_are_bounded() {
    let limits = DownloadLimits::default();

    assert_eq!(Duration::from_secs(10), limits.connect_timeout);
    assert_eq!(Duration::from_secs(60), limits.total_timeout);
    assert_eq!(5, limits.max_redirects);
}

fn runtime() -> tokio::runtime::Runtime {
    tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap()
}

fn test_limits(total_timeout: Duration, max_redirects: usize) -> DownloadLimits {
    DownloadLimits {
        connect_timeout: Duration::from_secs(1),
        total_timeout,
        max_redirects,
    }
}

fn spawn_server(
    expected_requests: usize,
    handler: impl Fn(usize, &str) -> String + Send + Sync + 'static,
) -> (String, thread::JoinHandle<usize>) {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    listener.set_nonblocking(true).unwrap();
    let address = listener.local_addr().unwrap();
    let handler = std::sync::Arc::new(handler);
    let server = thread::spawn(move || {
        let deadline = Instant::now() + Duration::from_secs(3);
        let mut handled = 0;
        while handled < expected_requests && Instant::now() < deadline {
            match listener.accept() {
                Ok((mut stream, _peer)) => {
                    let path = read_request_path(&mut stream);
                    let response = handler(handled, &path);
                    let _ = stream.write_all(response.as_bytes());
                    let _ = stream.flush();
                    handled += 1;
                }
                Err(error) if error.kind() == std::io::ErrorKind::WouldBlock => {
                    thread::sleep(Duration::from_millis(5));
                }
                Err(error) => panic!("test server failed to accept connection: {error}"),
            }
        }
        handled
    });

    (format!("http://{address}/start"), server)
}

fn read_request_path(stream: &mut TcpStream) -> String {
    stream
        .set_read_timeout(Some(Duration::from_secs(1)))
        .unwrap();
    let mut request = Vec::new();
    let mut buffer = [0_u8; 1024];
    while !request.ends_with(b"\r\n\r\n") {
        let read = stream.read(&mut buffer).unwrap();
        if read == 0 {
            break;
        }
        request.extend_from_slice(&buffer[..read]);
    }
    String::from_utf8_lossy(&request)
        .lines()
        .next()
        .and_then(|line| line.split_whitespace().nth(1))
        .unwrap_or("/")
        .to_string()
}

fn redirect_response(location: &str) -> String {
    format!(
        "HTTP/1.1 302 Found\r\nLocation: {location}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
    )
}

fn ok_response(body: &str) -> String {
    format!(
        "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nContent-Type: text/yaml\r\nConnection: close\r\n\r\n{body}",
        body.len()
    )
}

fn unique_temp_path(label: &str) -> PathBuf {
    static NEXT_ID: AtomicU64 = AtomicU64::new(0);
    std::env::temp_dir().join(format!(
        "chimera-download-{}-{}-{label}",
        std::process::id(),
        NEXT_ID.fetch_add(1, Ordering::Relaxed)
    ))
}

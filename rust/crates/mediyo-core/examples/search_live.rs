use mediyo_core::api::search::search;
use mediyo_core::Session;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let mut session = Session::new();
    session.fetch_visitor_data()?;
    println!("visitor_data: {:?}", session.context().client.visitor_data);

    let resp = search(&session, "drake")?;
    println!(
        "results: {}  filters: {}",
        resp.results.len(),
        resp.filters.len()
    );
    for (i, r) in resp.results.iter().take(10).enumerate() {
        println!(
            "[{i:2}] {:?}  {:?}  title={:?}  artists={:?}  id={:?}",
            r.category,
            r.info,
            r.title,
            r.artists
                .iter()
                .map(|a| a.name.as_str())
                .collect::<Vec<_>>(),
            r.video_id.as_ref().or(r.browse_id.as_ref()),
        );
    }
    Ok(())
}

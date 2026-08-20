use mediyo_core::Session;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let mut session = Session::new();
    match session.fetch_visitor_data() {
        Ok(vd) => println!("visitor_data: {vd}"),
        Err(e) => println!("visitor_data error: {e:?}"),
    }
    Ok(())
}

package ee.zone.web.protokollitaja.backend.entities

case class Subtotal(
                      series: List[Series],
                      label: String,
                      subtotal: String
                    )

package ee.zone.web.protokollitaja.backend.entities

case class Subtotals(
                      series: List[Series],
                      label: String,
                      subtotal: String
                    )

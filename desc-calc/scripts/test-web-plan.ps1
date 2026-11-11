param([string]$BaseUrl = 'http://localhost:18080')
$ErrorActionPreference = 'Stop'

function Assert-Contains($Content, $Expected) {
    if (!$Content.Contains($Expected)) { throw "Missing expected response content: $Expected" }
}

$page = Invoke-WebRequest "$BaseUrl/flight-plan/csv" -UseBasicParsing
Assert-Contains $page.Content 'Generate descent plan'
Assert-Contains $page.Content 'waypoints[0].name'
Assert-Contains $page.Content 'Initial descent speed'
foreach ($setting in @{ cruiseAltitude = 39000; tdExtraDistance = 5; preparationLeadDistance = 40 }.GetEnumerator()) {
    if ($page.Content -notmatch ('<input[^>]*name="' + $setting.Key + '"[^>]*value="' + $setting.Value + '"')) {
        throw "Incorrect default setting: $($setting.Key)"
    }
}
if ($page.Content.IndexOf('id="cruiseAltitude"') -gt $page.Content.IndexOf('class="table-toolbar"')) {
    throw 'Cruise altitude must appear above the waypoint table'
}
if ($page.Content -notmatch '<input[^>]*class="waypoint-name"[^>]*readonly') {
    throw 'Waypoint names must be read-only in the editor'
}

$body = @{ cruiseAltitude = '39000'; initialSpeed = '290'; tdExtraDistance = '7'; preparationLeadDistance = '35' }
$rows = Import-Csv (Join-Path $PSScriptRoot '../src/main/resources/waypoints-paste.csv')
for ($i = 0; $i -lt $rows.Count; $i++) {
    foreach ($field in @('idx','name','distanceToNext','altHold','altAbove','altBelow','speed')) {
        $body["waypoints[$i].$field"] = [string]$rows[$i].$field
    }
}
# This constrained point must appear in the itinerary under the edited name.
$body['waypoints[5].name'] = 'WEBTEST'
$body['waypoints[5].altHold'] = '5100'
$body['waypoints[5].distanceToNext'] = '9'
$response = Invoke-WebRequest "$BaseUrl/flight-plan" -Method Post -Body $body -UseBasicParsing
Assert-Contains $response.Content 'class="itinerary"'
Assert-Contains $response.Content 'WEBTEST ('
Assert-Contains $response.Content 'HOLD 5100'
Assert-Contains $response.Content 'class="descent-table"'
Assert-Contains $response.Content 'To runway'
if ($response.Content -notmatch '(?s)<th scope="row">WEBTEST</th>\s*<td class="step-distance">9</td>') {
    throw 'Result distance must use the edited outbound leg length, not destination distance'
}
Assert-Contains $response.Content 'Back to flight plan'
Assert-Contains $response.Content 'class="plan-back-arrow"'
if ($response.Content.IndexOf('class="plan-back-arrow"') -gt $response.Content.IndexOf('>CRUISE')) {
    throw 'Result back arrow must precede the cruise setting'
}

$table = [regex]::Match($response.Content, '(?s)<table class="descent-table">(.*?)</table>').Groups[1].Value
if ([regex]::Matches($table, '<th scope="row">WEBTEST</th>').Count -ne 1) {
    throw 'Actions at the edited waypoint must share one table row'
}

# All waypoint-tagged actions from the raw itinerary must survive grouping, in order.
$rawPlan = [System.Net.WebUtility]::HtmlDecode(
    [regex]::Match($response.Content, '(?s)<pre class="itinerary">(.*?)</pre>').Groups[1].Value)
$reference = [regex]::Match($rawPlan, 'T/D\s+--\s+(.+) \+ (\d+)\s+--')
if (!$reference.Success) { throw 'Missing calculated T/D reference' }
$referenceName = $reference.Groups[1].Value
$referenceDistance = [int]$reference.Groups[2].Value
Assert-Contains $response.Content ('id="adjusted-td">' + $referenceName + ' + ' + ($referenceDistance + 7) + '</h2>')
Assert-Contains $response.Content ('id="start-preparation">' + $referenceName + ' + ' + ($referenceDistance + 42) + '</h2>')

# Changing only display margins must not affect the calculated text or action table.
$marginBody = $body.Clone()
$marginBody.tdExtraDistance = '0'
$marginBody.preparationLeadDistance = '0'
$withoutMargins = Invoke-WebRequest "$BaseUrl/flight-plan" -Method Post -Body $marginBody -UseBasicParsing
$unshiftedRaw = [System.Net.WebUtility]::HtmlDecode(
    [regex]::Match($withoutMargins.Content, '(?s)<pre class="itinerary">(.*?)</pre>').Groups[1].Value)
if ($unshiftedRaw -ne $rawPlan) { throw 'Display margins changed the calculated itinerary' }
Assert-Contains $withoutMargins.Content ('id="adjusted-td">' + $referenceName + ' + ' + $referenceDistance + '</h2>')
Assert-Contains $withoutMargins.Content ('id="start-preparation">' + $referenceName + ' + ' + $referenceDistance + '</h2>')
$tableActions = @([regex]::Matches($table, '<li>(.*?)</li>') | ForEach-Object {
    [System.Net.WebUtility]::HtmlDecode($_.Groups[1].Value)
})
$actionIndex = 0
foreach ($action in [regex]::Matches($rawPlan, '(?m)^.+ \(\d+\)  --  (.+)\r?$')) {
    $instruction = $action.Groups[1].Value.TrimEnd()
    while ($actionIndex -lt $tableActions.Count -and $tableActions[$actionIndex] -ne $instruction) {
        $actionIndex++
    }
    if ($actionIndex -ge $tableActions.Count) { throw "Missing or reordered grouped action: $instruction" }
    $actionIndex++
}
if ($response.Content.Contains('class="waypoint-table"')) { throw 'Result must use a separate screen' }

# Submit the actual hidden return form to prove it preserves the edited inputs.
$returnBody = @{}
foreach ($inputTag in [regex]::Matches($response.Content, '<input\b[^>]*>')) {
    $name = [regex]::Match($inputTag.Value, 'name="([^"]*)"').Groups[1].Value
    $value = [regex]::Match($inputTag.Value, 'value="([^"]*)"').Groups[1].Value
    $returnBody[[System.Net.WebUtility]::HtmlDecode($name)] = [System.Net.WebUtility]::HtmlDecode($value)
}
$editor = Invoke-WebRequest "$BaseUrl/flight-plan/edit" -Method Post -Body $returnBody -UseBasicParsing
Assert-Contains $editor.Content 'class="waypoint-table"'
Assert-Contains $editor.Content 'WEBTEST'
Assert-Contains $editor.Content 'value="5100"'
Assert-Contains $editor.Content 'value="7"'
Assert-Contains $editor.Content 'value="35"'
if ($editor.Content.Contains('class="descent-table"')) { throw 'Back action recalculated instead of opening the editor' }

# A second calculation must reflect its own edits, with no stale previous result.
$body['waypoints[5].name'] = 'SECONDTEST'
$body['waypoints[5].altHold'] = '5200'
$response = Invoke-WebRequest "$BaseUrl/flight-plan" -Method Post -Body $body -UseBasicParsing
Assert-Contains $response.Content 'SECONDTEST ('
Assert-Contains $response.Content 'HOLD 5200'
if ($response.Content.Contains('WEBTEST')) { throw 'Previous route leaked into the result' }

$body['waypoints[5].altAbove'] = '6000'
$response = Invoke-WebRequest "$BaseUrl/flight-plan" -Method Post -Body $body -UseBasicParsing
Assert-Contains $response.Content 'Altitude constraints conflict'
if ($response.Content.Contains('class="itinerary"')) { throw 'Invalid constraints produced a plan' }

$body['waypoints[5].altAbove'] = ''
$body['waypoints[5].distanceToNext'] = 'not-a-number'
$response = Invoke-WebRequest "$BaseUrl/flight-plan" -Method Post -Body $body -UseBasicParsing
Assert-Contains $response.Content 'Please check your inputs'
Assert-Contains $response.Content 'Please enter a whole number'
Assert-Contains $response.Content 'SECONDTEST'

$restored = Invoke-WebRequest "$BaseUrl/flight-plan/csv" -UseBasicParsing
Assert-Contains $restored.Content 'GWN30'
if ($restored.Content.Contains('SECONDTEST')) { throw 'GET did not restore the bundled CSV' }
$body['waypoints[5].distanceToNext'] = '9'
$body.tdExtraDistance = '-1'
$invalidMargin = Invoke-WebRequest "$BaseUrl/flight-plan" -Method Post -Body $body -UseBasicParsing
Assert-Contains $invalidMargin.Content 'Enter T/D extra distance and preparation lead distance'
Write-Output 'PASS: import, edited inputs, repeated calculations, constraint errors, numeric binding, CSV unchanged.'

[CmdletBinding()]
param(
    [string]$DatabaseSnapshot = 'docs/verification/database-schema-2026-10-31.json',
    [string]$DiagramSnapshot = 'docs/verification/drawio-erd-2026-10-31.json',
    [string]$Output = 'docs/verification/schema-comparison-2026-10-31.json'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$db = Get-Content -LiteralPath (Join-Path $projectRoot $DatabaseSnapshot) -Raw -Encoding UTF8 | ConvertFrom-Json
$erd = Get-Content -LiteralPath (Join-Path $projectRoot $DiagramSnapshot) -Raw -Encoding UTF8 | ConvertFrom-Json
$differences = [Collections.Generic.List[object]]::new()

function Add-Difference($kind, $table, $field, $diagramValue, $databaseValue) {
    $differences.Add([pscustomobject][ordered]@{
        kind = $kind; table = $table; field = $field
        diagram = $diagramValue; database = $databaseValue
    })
}
function Normalize-Type([string]$type) {
    $type = ($type -replace '\s+NN$', '').ToUpperInvariant() -replace '\s+', ''
    switch ($type) {
        'INT' { return 'INTEGER' }
        'SERIAL' { return 'INTEGER' }
        'BIGSERIAL' { return 'BIGINT' }
        'TIMESTAMPTZ' { return 'TIMESTAMPWITHTIMEZONE' }
        default { return $type }
    }
}
function Database-Type($column) {
    switch ($column.data_type) {
        'character varying' { return 'VARCHAR(' + $column.character_maximum_length + ')' }
        'numeric' { return 'NUMERIC(' + $column.numeric_precision + ',' + $column.numeric_scale + ')' }
        default { return Normalize-Type $column.data_type }
    }
}
function Normalize-Sql([string]$sql) {
    $sql = $sql.ToLowerInvariant()
    $sql = $sql -replace '::(character varying|text|numeric|integer|bigint|regclass)', ''
    $sql = $sql -replace '\bpublic\.', ''
    $sql = $sql -replace '\s+using btree', ''
    return ($sql -replace '[\s()]', '')
}
$domainTables = @($db.tables | Where-Object table_name -ne 'flyway_schema_history')
$diagramNames = @($erd.tables.name)
$databaseNames = @($domainTables.table_name)
foreach ($name in $diagramNames) {
    if ($databaseNames -notcontains $name) { Add-Difference 'missing_table' $name $null $name $null }
}
foreach ($name in $databaseNames) {
    if ($diagramNames -notcontains $name) { Add-Difference 'extra_table' $name $null $null $name }
}
$summary = foreach ($table in $erd.tables) {
    $actual = @($db.columns | Where-Object table_name -eq $table.name)
    foreach ($column in $table.columns) {
        $found = @($actual | Where-Object column_name -eq $column.name)
        if ($found.Count -ne 1) {
            Add-Difference 'missing_column' $table.name $column.name $column.type $null
            continue
        }
        if ((Normalize-Type $column.type) -ne (Database-Type $found[0])) {
            Add-Difference 'column_type' $table.name $column.name $column.type (Database-Type $found[0])
        }
        $defaultMatch = [regex]::Match($column.definition, '(?i)\bDEFAULT\s+(.+)$')
        $serial = $column.type -match '^(BIG)?SERIAL\b'
        if ($serial) {
            if ($found[0].column_default -notmatch "^nextval\(") {
                Add-Difference 'sequence_default' $table.name $column.name 'nextval(...)' $found[0].column_default
            }
        } else {
            $expectedDefault = if ($defaultMatch.Success) { $defaultMatch.Groups[1].Value } else { '' }
            if ((Normalize-Sql $expectedDefault) -ne (Normalize-Sql ([string]$found[0].column_default))) {
                Add-Difference 'default' $table.name $column.name $expectedDefault $found[0].column_default
            }
        }
        $nullable = if ($column.type -match '\bNN$') { 'NO' } else { 'YES' }
        if ($nullable -ne $found[0].is_nullable) {
            Add-Difference 'nullability' $table.name $column.name $nullable $found[0].is_nullable
        }
    }
    foreach ($column in $actual) {
        if (@($table.columns.name) -notcontains $column.column_name) {
            Add-Difference 'extra_column' $table.name $column.column_name $null $column.data_type
        }
    }
    $constraints = [regex]::Matches($table.constraints, '(?ms)ADD CONSTRAINT (\w+)\s+([\s\S]*?)(?=\nADD CONSTRAINT|\nALTER COLUMN|\nCREATE|\z)')
    foreach ($constraint in $constraints) {
        $name = $constraint.Groups[1].Value
        $found = @($db.constraints | Where-Object { $_.table_name -eq $table.name -and $_.conname -eq $name })
        if ($found.Count -ne 1) {
            Add-Difference 'missing_constraint' $table.name $name $constraint.Groups[2].Value $null
        } elseif ((Normalize-Sql $constraint.Groups[2].Value) -ne (Normalize-Sql $found[0].definition)) {
            Add-Difference 'constraint_definition' $table.name $name $constraint.Groups[2].Value $found[0].definition
        }
    }
    foreach ($constraint in @($db.constraints | Where-Object table_name -eq $table.name)) {
        if (@($constraints | ForEach-Object { $_.Groups[1].Value }) -notcontains $constraint.conname) {
            Add-Difference 'extra_constraint' $table.name $constraint.conname $null $constraint.definition
        }
    }
    $indexes = [regex]::Matches($table.constraints, '(?ms)(CREATE (?:UNIQUE )?INDEX (\w+) [\s\S]*?)(?=\nCREATE|\z)')
    foreach ($index in $indexes) {
        $name = $index.Groups[2].Value
        $found = @($db.indexes | Where-Object indexname -eq $name)
        if ($found.Count -ne 1) {
            Add-Difference 'missing_index' $table.name $name $index.Groups[1].Value $null
        } elseif ((Normalize-Sql $index.Groups[1].Value) -ne (Normalize-Sql $found[0].indexdef)) {
            Add-Difference 'index_definition' $table.name $name $index.Groups[1].Value $found[0].indexdef
        }
    }
    foreach ($index in @($db.indexes | Where-Object tablename -eq $table.name)) {
        $backingConstraint = @($db.constraints | Where-Object conname -eq $index.indexname).Count -gt 0
        if (-not $backingConstraint -and @($indexes | ForEach-Object { $_.Groups[2].Value }) -notcontains $index.indexname) {
            Add-Difference 'extra_index' $table.name $index.indexname $null $index.indexdef
        }
    }
    [ordered]@{ table = $table.name; diagramColumns = $table.columns.Count; databaseColumns = $actual.Count }
}
$result = [ordered]@{
    source = $erd.source; sourceModifiedTime = $erd.modifiedTime; page = $erd.page
    domainTables = $domainTables.Count
    domainColumns = @($db.columns | Where-Object table_name -ne 'flyway_schema_history').Count
    constraints = @($db.constraints | Where-Object table_name -ne 'flyway_schema_history').Count
    foreignKeys = @($db.constraints | Where-Object contype -eq 'f').Count
    indexes = @($db.indexes | Where-Object tablename -ne 'flyway_schema_history').Count
    excludedInfrastructureTable = 'flyway_schema_history'
    tableSummary = @($summary)
    differences = @($differences.ToArray())
    note = 'SQL comparison normalizes PostgreSQL casts, btree, whitespace and parentheses. Column defaults are compared; sequence limits require separate review. This is not a SQL equivalence proof.'
}
[IO.File]::WriteAllText((Join-Path $projectRoot $Output), ($result | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
[pscustomobject]$result | Select-Object domainTables, domainColumns, constraints, foreignKeys, indexes
$differences | Format-Table kind, table, field -AutoSize

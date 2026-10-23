# Telecharge des livres du projet Gutenberg (format texte brut).
#
# Usage (depuis le dossier daar-projet1, dans PowerShell) :
#   powershell -ExecutionPolicy Bypass -File tests\scripts\telecharger_livres.ps1 tests\livres_tests.txt tests\data
#   powershell -ExecutionPolicy Bypass -File tests\scripts\telecharger_livres.ps1 tests\livres_bench.txt bench_data
#
# Chaque ligne du fichier liste commence par un numero Gutenberg (le reste est un commentaire).
# Une pause de 2 secondes separe les telechargements, pour respecter le site.

param(
    [Parameter(Mandatory = $true)] [string] $Liste,
    [Parameter(Mandatory = $true)] [string] $Dossier
)

New-Item -ItemType Directory -Force -Path $Dossier | Out-Null

foreach ($ligne in Get-Content $Liste) {
    $ligne = $ligne.Trim()
    if ($ligne -eq "" -or $ligne.StartsWith("#")) { continue }
    $id = ($ligne -split "\s+")[0]
    $fichier = Join-Path $Dossier "$id.txt"

    if (Test-Path $fichier) {
        Write-Host "deja present : $id.txt"
        continue
    }
    $url = "https://www.gutenberg.org/cache/epub/$id/pg$id.txt"
    try {
        Invoke-WebRequest -Uri $url -OutFile $fichier -UseBasicParsing
        $ko = [math]::Round((Get-Item $fichier).Length / 1KB)
        Write-Host "OK : $id.txt ($ko Ko)"
    } catch {
        Write-Host "ECHEC : $id ($url)"
        if (Test-Path $fichier) { Remove-Item $fichier }
    }
    Start-Sleep -Seconds 2
}
Write-Host "Termine."

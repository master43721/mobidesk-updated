param (
    [string]$username,
    [string]$password
)

# Create the user
$securePassword = ConvertTo-SecureString $password -AsPlainText -Force
New-LocalUser -Name $username -Password $securePassword -Description "MobiLab Remote User"

# Add to Remote Desktop Users group
Add-LocalGroupMember -Group "Remote Desktop Users" -Member $username

# Force password to not expire for lab use (optional)
Set-LocalUser -Name $username -PasswordNeverExpires $true

Write-Output "User $username created successfully"
